package com.commercehub.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequest {


    @NotBlank(message = "Credential từ Google không được để trống!")
    // Chuỗi mã hóa (Credential/ID Token) do Google cấp cho Frontend
    private String credential;

    // deviceId quản lý thiết bị
    private String deviceId;
}