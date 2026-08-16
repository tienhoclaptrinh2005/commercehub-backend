package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProductRequest {
    String name;
    Long categoryId;
    String shortDescription;
    String description;
    String status;

    @Size(max = 500, message = "Đường dẫn ảnh sản phẩm không được vượt quá 500 ký tự!")
    String thumbnailUrl;
}
