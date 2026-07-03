package com.commercehub.backend.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;


@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateProductRequest {


    @NotNull(message = "Danh mục không được để trống!")
    Long categoryId;

    @NotBlank(message = "Tên sản phẩm không được để trống!")
    String name;

    @NotBlank(message = "Mô tả ngắn sản phẩm không được để trống!")
    String shortDescription;

    @NotBlank(message = "Mô tả  sản phẩm không được để trống!")
    String description;

    @Pattern(regexp = "^(ACCOUNT|OTHER)$",
            message = "Loại sản phẩm không hợp lệ! Chỉ chấp nhận ACCOUNT hoặc OTHER.")
    String productType = "ACCOUNT";

    @Pattern(regexp = "^(INSTANT|PRE_ORDER)$",
            message = "Hình thức giao hàng không hợp lệ! Vui lòng chọn INSTANT hoặc PRE_ORDER.")
    String deliveryType = "INSTANT";

    List<String> imageUrls;


    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 gói/biến thể!")
    @Valid
    List<CreateVariantRequest> variants;

}
