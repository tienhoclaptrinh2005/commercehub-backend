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
    String sellerUsername;
    String sellerAvatarUrl;
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
    BigDecimal averageRating;
    Long reviewCount;
    String thumbnailUrl;
    Integer stockCount;

    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;

    List<ProductVariantResponse> variants;
    BigDecimal minPrice;
}
