package com.tfl.estore.ordersservice.core.events;

import com.tfl.estore.ordersservice.core.data.OrderStatus;
import lombok.Value;

@Value
public class OrderApprovedEvent {
    String orderId;
    OrderStatus orderStatus = OrderStatus.APPROVED;
}
