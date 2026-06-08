package com.commercehub.backend.auth.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequest {
    // Chuỗi mã hóa (Credential/ID Token) do Google cấp cho Frontend
    private String credential;

    // Có thể thêm deviceId nếu bạn muốn quản lý thiết bị
    private String deviceId;
}