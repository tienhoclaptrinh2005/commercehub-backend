package com.commercehub.backend.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutRequest {

    @NotNull(message = "ID Phân loại sản phẩm (Variant) không được để trống")
    Long productVariantId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng mua ít nhất là 1")
    Integer quantity;


}