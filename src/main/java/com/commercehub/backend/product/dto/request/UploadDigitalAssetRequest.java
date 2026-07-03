package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadDigitalAssetRequest {

    @NotNull(message = "Vui lòng chọn gói sản phẩm cần nạp hàng!")
    Long variantId;

    @NotEmpty(message = "Danh sách tài khoản nạp vào không được để trống!")
    List<String> rawAssets;
}