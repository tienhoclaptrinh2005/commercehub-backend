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
    String assetData;
    String status;
    OffsetDateTime createdAt;
}