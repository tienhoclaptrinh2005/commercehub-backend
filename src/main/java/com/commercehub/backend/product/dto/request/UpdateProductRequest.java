package com.commercehub.backend.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProductRequest {
    @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
    String name;
    Long categoryId;

    @Size(max = 200, message = "Mô tả ngắn tối đa 200 ký tự")
    String shortDescription;

    @Size(max = 50000, message = "Mô tả sản phẩm tối đa 50000 ký tự")
    String description;

    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Trạng thái sản phẩm không hợp lệ")
    String status;

    @Size(max = 500, message = "Đường dẫn ảnh sản phẩm không được vượt quá 500 ký tự!")
    String thumbnailUrl;

    @Valid
    @Size(min = 1, max = 5, message = "Sản phẩm phải có từ 1 đến 5 biến thể")
    java.util.List<UpdateVariantRequest> variants;
}
