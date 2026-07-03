package com.commercehub.backend.product.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductImageResponse {
    Long id;
    String imageUrl;
    Integer sortOrder;
}