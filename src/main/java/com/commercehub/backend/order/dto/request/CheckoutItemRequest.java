package com.commercehub.backend.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutItemRequest {

    @NotNull(message = "ID Phân loại sản phẩm không được để trống")
    Long productVariantId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng mua ít nhất là 1")
    @Max(value = 1000, message = "Số lượng mua tối đa trong một lần là 1000")
    Integer quantity;

    String buyerInputs;
}