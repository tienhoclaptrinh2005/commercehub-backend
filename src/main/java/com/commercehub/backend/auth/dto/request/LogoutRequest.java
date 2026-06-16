package com.commercehub.backend.auth.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LogoutRequest {

    @NotBlank(message = "Refresh Token không được để trống!")
    private String refreshToken;
}