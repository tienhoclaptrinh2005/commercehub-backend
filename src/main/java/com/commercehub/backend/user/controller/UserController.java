package com.commercehub.backend.user.controller;

import com.commercehub.backend.auth.dto.request.ChangePasswordRequest;
import com.commercehub.backend.auth.service.AuthService;
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

    // Lấy thông tin public của một user bất kỳ thông qua ID
    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }


    @PostMapping("/me/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        authService.changePassword(currentUser.getUsername(), request);
        return ResponseEntity.ok("Đổi mật khẩu thành công!");
    }

    @GetMapping("/levels")
    public ResponseEntity<List<UserLevelResponse>> getAllLevels() {
        return ResponseEntity.ok(userLevelService.getAllLevelConfigs());
    }


}