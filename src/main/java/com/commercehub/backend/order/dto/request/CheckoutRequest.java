package com.commercehub.backend.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutRequest {

    @NotEmpty(message = "Giỏ hàng không được để trống")
    @Valid
    List<CheckoutItemRequest> items;

    // Tùy chọn phương thức thanh toán (mặc định là ví)
    String paymentMethod = "WALLET";
}