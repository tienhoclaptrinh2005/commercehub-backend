package com.commercehub.backend.auth.controller;

import com.commercehub.backend.auth.dto.request.*;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.auth.service.AuthService;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${auth.refresh-cookie.name:commercehub_refresh}")
    private String refreshCookieName;

    @Value("${auth.refresh-cookie.secure:false}")
    private boolean refreshCookieSecure;

    @Value("${auth.refresh-cookie.same-site:Strict}")
    private String refreshCookieSameSite;

    @Value("${auth.refresh-cookie.max-age-seconds:604800}")
    private long refreshCookieMaxAgeSeconds;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);

        ApiResponse<AuthResponse> body = ApiResponse.<AuthResponse>builder()
                .success(true)
                .code(201)
                .message("Đăng ký tài khoản thành công!")
                .data(response)
                .build();
        return withRefreshCookie(HttpStatus.CREATED, body, response.getRefreshToken());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return withRefreshCookie(
                HttpStatus.OK,
                ApiResponse.success("Đăng nhập thành công", response),
                response.getRefreshToken()
        );
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = "${auth.refresh-cookie.name:commercehub_refresh}", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        AuthResponse response = authService.refreshToken(refreshToken);
        return withRefreshCookie(
                HttpStatus.OK,
                ApiResponse.success("Làm mới Token thành công!", response),
                response.getRefreshToken()
        );
    }


    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "${auth.refresh-cookie.name:commercehub_refresh}", required = false) String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.success("Đăng xuất thành công!", null));
    }


    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.googleLogin(request);
        return withRefreshCookie(
                HttpStatus.OK,
                ApiResponse.success("Đăng nhập bằng Google thành công!", response),
                response.getRefreshToken()
        );
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(
            HttpStatus status,
            ApiResponse<AuthResponse> body,
            String refreshToken
    ) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, createRefreshCookie(refreshToken).toString())
                .body(body);
    }

    private ResponseCookie createRefreshCookie(String refreshToken) {
        return ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(refreshCookieMaxAgeSeconds))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

}
