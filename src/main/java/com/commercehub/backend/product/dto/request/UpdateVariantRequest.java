package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
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

    @DecimalMin(value = "0.0", inclusive = false, message = "Giá bán phải lớn hơn 0!")
    BigDecimal price;

    Integer sortOrder;

    String status;
}
