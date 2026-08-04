package com.commercehub.backend.cart.dto.request;

import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "Idempotency key tối đa 100 ký tự")
    String idempotencyKey;

    List<CartBuyerInput> buyerInputs;

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CartBuyerInput {
        Long productVariantId;
        String buyerInputs;
    }
}
