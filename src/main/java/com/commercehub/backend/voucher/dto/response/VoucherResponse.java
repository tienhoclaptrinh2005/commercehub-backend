package com.commercehub.backend.voucher.dto.response;

import com.commercehub.backend.voucher.entity.VoucherDiscountType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

@Builder
public record VoucherResponse(
        Long id,
        String code,
        String description,
        VoucherDiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        boolean applyAllProducts,
        Set<Long> productIds,
        OffsetDateTime startsAt,
        OffsetDateTime expiresAt,
        Integer usageLimit,
        Integer usedCount,
        boolean active,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
