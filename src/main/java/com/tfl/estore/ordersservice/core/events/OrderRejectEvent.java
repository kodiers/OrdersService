package com.tfl.estore.ordersservice.core.events;

import com.tfl.estore.ordersservice.core.data.OrderStatus;
import lombok.Value;

@Value
public class OrderRejectEvent {
    String orderId;
    String reason;
    OrderStatus orderStatus = OrderStatus.REJECTED;
}
