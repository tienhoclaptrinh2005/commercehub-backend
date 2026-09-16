package com.commercehub.backend.voucher.dto.response;

import java.math.BigDecimal;

public record VoucherPreviewResponse(
        Long voucherId,
        String code,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount
) {
}
