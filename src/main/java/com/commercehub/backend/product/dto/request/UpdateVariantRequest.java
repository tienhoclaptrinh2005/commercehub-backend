package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateVariantRequest {

    // Có giá trị khi request được gửi lồng trong PUT /seller/products/{id}.
    Long id;

    @Size(max = 100, message = "Tên biến thể tối đa 100 ký tự")
    String name;

    @Min(value = 1, message = "Thời hạn sử dụng tối thiểu là 1 ngày!")
    @Max(value = 36500, message = "Thời hạn sử dụng tối đa 36500 ngày")
    Integer durationDays;

    @DecimalMin(value = "0.0", inclusive = false, message = "Giá bán phải lớn hơn 0!")
    @DecimalMax(value = "500000000", message = "Giá bán tối đa 500.000.000đ")
    BigDecimal price;

    Integer sortOrder;

    String status;
}
