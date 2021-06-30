package com.tfl.estore.ordersservice.query;

import com.tfl.estore.ordersservice.core.data.OrderEntity;
import com.tfl.estore.ordersservice.core.data.OrderSummary;
import com.tfl.estore.ordersservice.core.data.OrdersRepository;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
public class OrdersQueryHandler {

    private final OrdersRepository ordersRepository;

    public OrdersQueryHandler(OrdersRepository ordersRepository) {
        this.ordersRepository = ordersRepository;
    }

    @QueryHandler
    public OrderSummary findOrder(FindOrderQuery findOrderQuery) {
        OrderEntity orderEntity = ordersRepository.findByOrderId(findOrderQuery.getOrderId());
        return new OrderSummary(orderEntity.getOrderId(), orderEntity.getOrderStatus(), "");
    }
}
