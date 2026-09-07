package com.commercehub.backend.product.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SellerProductListItemResponse(
        Long id,
        String name,
        String slug,
        Long categoryId,
        String categoryName,
        String productType,
        String deliveryType,
        String status,
        long soldCount,
        String thumbnailUrl,
        BigDecimal minPrice,
        long stockCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
