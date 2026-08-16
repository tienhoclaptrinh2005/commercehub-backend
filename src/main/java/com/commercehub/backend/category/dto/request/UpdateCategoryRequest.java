package com.commercehub.backend.category.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCategoryRequest {
    @Size(max = 100 , message = "Tên danh mục không  được quá 100 ký tự !")
    String name;

    String iconUrl;

     Boolean isActive;

     Integer sortOrder;

     @Positive(message = "ID danh mục cha phải là số dương!")
     Long parentId;

}
