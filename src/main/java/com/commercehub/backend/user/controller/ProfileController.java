package com.commercehub.backend.user.controller;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.user.dto.request.UpdateProfileRequest;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(@AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED); // Báo lỗi 401
        }

            String email = currentUser.getUsername();
            ProfileResponse response = profileService.getMyProfile(email);


           return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hồ sơ thành công!", response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateMyProfile (@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal CustomUserDetails currentUser) {
        String email = currentUser.getUsername();
        ProfileResponse response = profileService.updateMyProfile(email, request);

        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công !" , response));

    }
}
