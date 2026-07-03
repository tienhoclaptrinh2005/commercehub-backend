package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateVariantRequest {

    @NotNull(message = "ID Sản phẩm không được để trống!")
    Long productId;

    @NotBlank(message = "Tên gói/biến thể không được để trống! (VD: Gói 1 Tháng, Gia hạn 1 Năm)")
    String name;

    @Min(value = 1, message = "Thời hạn sử dụng tối thiểu là 1 ngày!")
    Integer durationDays;

    @NotNull(message = "Giá bán không được để trống!")
    @Min(value = 0, message = "Giá bán không được nhỏ hơn 0!")
    BigDecimal price;

    Integer sortOrder;
}