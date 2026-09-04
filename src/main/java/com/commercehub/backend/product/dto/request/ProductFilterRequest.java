package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Pattern;
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

    @Pattern(
            regexp = "^(INSTANT|PRE_ORDER)$",
            message = "Hình thức giao hàng không hợp lệ! Vui lòng chọn INSTANT hoặc PRE_ORDER."
    )
    String deliveryType;

}
