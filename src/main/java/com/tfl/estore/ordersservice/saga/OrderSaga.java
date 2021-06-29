package com.tfl.estore.ordersservice.saga;

import com.tfl.estore.core.commands.CancelProductReservationCommand;
import com.tfl.estore.core.commands.ProcessPaymentCommand;
import com.tfl.estore.core.commands.ReserveProductCommand;
import com.tfl.estore.core.events.PaymentProcessedEvent;
import com.tfl.estore.core.events.ProductReservationCanceledEvent;
import com.tfl.estore.core.events.ProductReservedEvent;
import com.tfl.estore.core.model.User;
import com.tfl.estore.core.query.FetchUserPaymentDetailsQuery;
import com.tfl.estore.ordersservice.command.ApproveOrderCommand;
import com.tfl.estore.ordersservice.command.RejectOrderCommand;
import com.tfl.estore.ordersservice.core.events.OrderApprovedEvent;
import com.tfl.estore.ordersservice.core.events.OrderCreatedEvent;
import com.tfl.estore.ordersservice.core.events.OrderRejectEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Saga
@Slf4j
public class OrderSaga {

    private final String PAYMENT_PROCESSING_TIMEOUT_DEADLINE = "payment-processing-deadline";

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient QueryGateway queryGateway;

    @Autowired
    private transient DeadlineManager deadlineManager;

    private String scheduleId;

    private void cancelProductReservation(ProductReservedEvent productReservedEvent, String reason) {
        cancelDeadline();
        CancelProductReservationCommand command = CancelProductReservationCommand.builder()
                .orderId(productReservedEvent.getOrderId())
                .productId(productReservedEvent.getProductId())
                .quantity(productReservedEvent.getQuantity())
                .userId(productReservedEvent.getUserId())
                .reason(reason)
                .build();
        commandGateway.send(command);
    }

    private void cancelDeadline() {
        if (scheduleId != null) {
            deadlineManager.cancelSchedule(PAYMENT_PROCESSING_TIMEOUT_DEADLINE, scheduleId);
            scheduleId = null;
        }

    }

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderCreatedEvent orderCreatedEvent) {
        ReserveProductCommand reserveProductCommand = ReserveProductCommand.builder()
                .orderId(orderCreatedEvent.getOrderId())
                .productId(orderCreatedEvent.getProductId())
                .quantity(orderCreatedEvent.getQuantity())
                .userId(orderCreatedEvent.getUserId())
                .build();
        log.info("OrderCreatedEvent handled for order: " + orderCreatedEvent.getOrderId());
        commandGateway.send(reserveProductCommand, new CommandCallback<ReserveProductCommand, Object>() {
            @Override
            public void onResult(CommandMessage<? extends ReserveProductCommand> commandMessage, CommandResultMessage<?> commandResultMessage) {
                if (commandResultMessage.isExceptional()) {
                    // start compensational transaction
                }
            }
        });
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(ProductReservedEvent productReservedEvent) {
        // Process user payment
        FetchUserPaymentDetailsQuery fetchUserPaymentDetailsQuery = new FetchUserPaymentDetailsQuery();
        fetchUserPaymentDetailsQuery.setUserId(productReservedEvent.getUserId());
        User user = null;
        try {
            user = queryGateway.query(fetchUserPaymentDetailsQuery, ResponseTypes.instanceOf(User.class)).join();
        } catch (Exception e) {
            log.error(e.getMessage());
            // start compensating transaction
            cancelProductReservation(productReservedEvent, e.getMessage());
            return;
        }
        if (user == null) {
            // start compensating transaction
            cancelProductReservation(productReservedEvent, "Could not fetch user");
            return;
        }
        log.info("Successfully get user");

        scheduleId = deadlineManager.schedule(Duration.of(10L, ChronoUnit.SECONDS),
                PAYMENT_PROCESSING_TIMEOUT_DEADLINE, productReservedEvent);

        ProcessPaymentCommand processPaymentCommand = ProcessPaymentCommand.builder()
                .orderId(productReservedEvent.getOrderId())
                .paymentDetails(user.getPaymentDetails())
                .paymentId(UUID.randomUUID().toString())
                .build();
        String result = null;
        try {
            result = commandGateway.sendAndWait(processPaymentCommand, 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // start compensating transaction
            log.error(e.getMessage());
            cancelProductReservation(productReservedEvent, e.getMessage());
            return;
        }
        if (result == null) {
            // start compensating transaction
            log.error("ProcessPaymentCommand failed: " + processPaymentCommand.getOrderId());
            cancelProductReservation(productReservedEvent, "ProcessPaymentCommand failed: " + processPaymentCommand.getOrderId());
            return;
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentProcessedEvent event) {
        cancelDeadline();
        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(event.getOrderId());
        commandGateway.send(approveOrderCommand);
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(ProductReservationCanceledEvent productReservationCanceledEvent) {
        // send RejectOrderCommand
        RejectOrderCommand rejectOrderCommand = new RejectOrderCommand(productReservationCanceledEvent.getOrderId(),
                productReservationCanceledEvent.getReason());
        commandGateway.send(rejectOrderCommand);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderRejectEvent orderRejectEvent) {
        log.info("Rejected order " + orderRejectEvent.getOrderId());
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderApprovedEvent orderApprovedEvent) {
        log.info("Order is approved: " + orderApprovedEvent.getOrderId());
//        SagaLifecycle.end();
    }

    @DeadlineHandler(deadlineName = PAYMENT_PROCESSING_TIMEOUT_DEADLINE)
    public void handlePaymentDeadline(ProductReservedEvent productReservedEvent) {
        log.info("Payment deadline reached");
        cancelProductReservation(productReservedEvent, "Payment timeout");
    }
}
