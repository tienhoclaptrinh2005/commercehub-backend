package com.commercehub.backend.auth.controller;

import com.commercehub.backend.auth.dto.request.*;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.auth.service.AuthService;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<AuthResponse>builder()
                .success(true)
                .code(201)
                .message("Đăng ký tài khoản thành công!")
                .data(response)
                .build()
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authService.login(request)));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Làm mới Token thành công!", authService.refreshToken(request)));
    }


    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request, @AuthenticationPrincipal CustomUserDetails currentUser) {
        authService.logout(request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công!", null));
    }


    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập bằng Google thành công!", authService.googleLogin(request)));
    }

}
