package com.commercehub.backend.product.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductResponse {
    Long id;
    Long shopId;
    String shopName;
    Long categoryId;
    String categoryName;

    String name;
    String slug;
    String shortDescription;
    String description;
    String productType;
    String deliveryType;
    String status;
    Long soldCount;
    String thumbnailUrl;
    Integer stockCount = 0;

    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;

    List<ProductVariantResponse> variants;
    List<String> imageUrls;
    BigDecimal minPrice;
}
