package com.commercehub.backend.product.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)

public class ProductVariantResponse
{
    Long id;
    Long productId;
    String name;
    Integer durationDays;
    BigDecimal price;
    Integer sortOrder;
    String status;
    Integer stockCount;
}

