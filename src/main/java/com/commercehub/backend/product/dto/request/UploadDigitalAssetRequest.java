package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
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
    @Size(max = 500, message = "Mỗi lần chỉ được nạp tối đa 500 tài khoản/key")
    List<@NotBlank(message = "Dữ liệu kho không được để trống")
            @Size(max = 10000, message = "Mỗi dòng dữ liệu kho tối đa 10000 ký tự") String> rawAssets;
}
