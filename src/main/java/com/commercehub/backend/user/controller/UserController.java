package com.commercehub.backend.user.controller;

import com.commercehub.backend.auth.dto.request.ChangePasswordRequest;
import com.commercehub.backend.auth.service.AuthService;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.user.dto.response.UserLevelResponse;
import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.service.UserLevelService;
import com.commercehub.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserLevelService userLevelService;
    private final AuthService authService;


    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(@PathVariable String username) {
        UserResponse response = userService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công!", response));
    }

    @PostMapping("/me/change-password")

    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        authService.changePassword(currentUser.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công!", null));
    }

    @GetMapping("/levels")
    public ResponseEntity<ApiResponse<List<UserLevelResponse>>> getAllLevels() {
        List<UserLevelResponse> response = userLevelService.getAllLevelConfigs();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách cấp độ thành công!", response));
    }
}