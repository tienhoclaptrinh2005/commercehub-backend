package com.commercehub.backend.user.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class UpdateProfileRequest {
    @NotBlank(message = "Họ và tên không được để trống!")
    @Size(min = 2, max = 50, message = "Họ và tên phải từ 2 đến 50 ký tự!")
    String fullName;
    @Pattern(regexp = "^(0[3|5|7|8|9])+([0-9]{8})$", message = "Số điện thoại không đúng định dạng!")
    String phone;
}
