package com.tfl.estore.ordersservice.command.rest;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Data
public class CreateOrderRestModel {
    @NotBlank(message = "productId is required field")
    private String productId;

    @Min(value = 1, message = "Quantity cannot be lower than 1")
    @Max(value = 5, message = "Quantity cannot be greater than 5")
    private Integer quantity;

    @NotBlank(message = "addressId is required field")
    private String addressId;
}
