package com.commercehub.backend.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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

    // Tùy chọn phương thức thanh toán (hiện tại hệ thống luôn trừ ví - nạp trước mua sau)
    String paymentMethod = "WALLET";

    /**
     * Khóa idempotency do client sinh (UUID) — gửi lại cùng key sẽ trả về
     * đơn đã tạo thay vì tạo đơn mới và trừ ví lần 2.
     */
    @Size(max = 100, message = "Idempotency key tối đa 100 ký tự")
    String idempotencyKey;
}