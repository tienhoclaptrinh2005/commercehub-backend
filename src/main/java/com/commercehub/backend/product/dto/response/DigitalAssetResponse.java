package com.commercehub.backend.product.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DigitalAssetResponse {
    Long id;
    Long variantId;
    String deliveryContent;   // nội dung sẽ giao cho khách (nguyên văn 1 dòng TXT)
    String assetData;
    String status;
    OffsetDateTime createdAt;
}