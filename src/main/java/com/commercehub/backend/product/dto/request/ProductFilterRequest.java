package com.commercehub.backend.product.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductFilterRequest {
    String keyword;
    Long categoryId;
    Long shopId;

}