package com.commercehub.backend.storage.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.storage.dto.request.CompleteAvatarImageRequest;
import com.commercehub.backend.storage.dto.request.PresignAvatarImageRequest;
import com.commercehub.backend.storage.dto.response.PresignAvatarImageResponse;
import com.commercehub.backend.storage.service.AvatarImageStorageService;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/users/me/avatar/uploads")
public class UserAvatarImageController {

    private final AvatarImageStorageService imageStorageService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignAvatarImageResponse>> presign(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody PresignAvatarImageRequest request
    ) {
        PresignAvatarImageResponse response = imageStorageService.createUpload(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo đường dẫn tải ảnh đại diện tạm thời!", response));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<ProfileResponse>> complete(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CompleteAvatarImageRequest request
    ) {
        ProfileResponse response = imageStorageService.completeAndApplyUpload(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Ảnh đại diện đã được cập nhật!", response));
    }
}
