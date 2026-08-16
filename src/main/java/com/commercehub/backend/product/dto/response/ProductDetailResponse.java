package com.commercehub.backend.product.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductDetailResponse {
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
    Long failedDisputeCount;
    String thumbnailUrl;
    OffsetDateTime createdAt;
    Integer stockCount;

    List<ProductVariantResponse> variants;

    // Nếu là hàng Đặt trước (PRE_ORDER) thì sẽ có object này
    PreOrderConfigResponse preOrderConfig;

}
