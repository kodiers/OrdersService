package com.tfl.estore.ordersservice.core.data;

import lombok.Value;

@Value
public class OrderSummary {
    String orderId;
    OrderStatus orderStatus;
    String message;
}
