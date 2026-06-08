package com.commercehub.backend.auth.dto.request;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LogoutRequest {
    private String refreshToken;
}