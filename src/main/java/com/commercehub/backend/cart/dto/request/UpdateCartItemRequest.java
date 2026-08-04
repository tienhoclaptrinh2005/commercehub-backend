package com.commercehub.backend.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCartItemRequest {

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng ít nhất là 1")
    @Max(value = 1000, message = "Số lượng tối đa là 1000")
    Integer quantity;
}
