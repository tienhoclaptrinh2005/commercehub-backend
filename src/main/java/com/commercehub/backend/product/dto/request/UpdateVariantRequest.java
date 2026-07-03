package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateVariantRequest {


    String name;

    @Min(value = 1, message = "Thời hạn sử dụng tối thiểu là 1 ngày!")
    Integer durationDays;

    @Min(value = 0, message = "Giá bán không được nhỏ hơn 0!")
    BigDecimal price;

    Integer sortOrder;

    String status;
}