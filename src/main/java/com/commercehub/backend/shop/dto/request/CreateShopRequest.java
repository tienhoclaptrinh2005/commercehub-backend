package com.commercehub.backend.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateShopRequest {

    @NotBlank(message = "Tên gian hàng không được để trống!")
    @Size(min = 5, max = 255, message = "Tên gian hàng phải từ 5 đến 255 ký tự!")
    private String name;

    @NotBlank(message = "Username không được để trống!")
    @Pattern(
            regexp = "^[a-zA-Z0-9_.]{3,100}$",
            message = "Username không hợp lệ! Chỉ dùng chữ, số, dấu chấm và dấu gạch dưới."
    )
    private String username;

    @NotBlank(message = "Thông tin liên hệ không được để trống!")
    @Size(max = 255, message = "Thông tin liên hệ tối đa 255 ký tự!")
    private String contactInfo;

    @Size(max = 500, message = "Lý do đăng ký tối đa 500 ký tự!")
    private String applicationReason;

    @AssertTrue(message = "Bạn cần đồng ý với quy chế người bán!")
    private boolean acceptedTerms;

    private String description;
    private String shopCoverUrl;
}
