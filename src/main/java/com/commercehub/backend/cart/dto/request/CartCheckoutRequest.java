package com.commercehub.backend.cart.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Checkout toàn bộ giỏ hàng.
 * buyerInputs: dữ liệu buyer nhập cho các sản phẩm PRE_ORDER
 * (map theo productVariantId).
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartCheckoutRequest {

    /** Idempotency key do client sinh — retry không tạo đơn/trừ ví lần 2. */
    @NotBlank(message = "Idempotency key không được để trống")
    @Size(min = 16, max = 100, message = "Idempotency key phải từ 16 đến 100 ký tự")
    String idempotencyKey;

    @Valid
    @Size(max = 50, message = "Tối đa 50 dòng thông tin đặt hàng")
    List<CartBuyerInput> buyerInputs;

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CartBuyerInput {
        @jakarta.validation.constraints.NotNull(message = "Variant không được để trống")
        Long productVariantId;

        @Size(max = 200, message = "Thông tin đặt hàng tối đa 200 ký tự")
        String buyerInputs;
    }
}
