package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
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

    // Không bắt buộc khi variant được gửi lồng trong request tạo sản phẩm.
    // Endpoint tạo variant riêng sẽ kiểm tra trường này ở service.
    Long productId;

    @NotBlank(message = "Tên gói/biến thể không được để trống! (VD: Gói 1 Tháng, Gia hạn 1 Năm)")
    String name;

    @Min(value = 1, message = "Thời hạn sử dụng tối thiểu là 1 ngày!")
    Integer durationDays;

    @NotNull(message = "Giá bán không được để trống!")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá bán phải lớn hơn 0!")
    BigDecimal price;

    Integer sortOrder;
}
