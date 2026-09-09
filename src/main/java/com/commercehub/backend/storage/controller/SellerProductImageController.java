package com.commercehub.backend.storage.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.storage.dto.request.CompleteProductImageRequest;
import com.commercehub.backend.storage.dto.request.PresignProductImageRequest;
import com.commercehub.backend.storage.dto.response.CompleteProductImageResponse;
import com.commercehub.backend.storage.dto.response.PresignProductImageResponse;
import com.commercehub.backend.storage.service.ProductImageStorageService;
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
@PreAuthorize("hasRole('SELLER')")
@RequestMapping("/api/v1/seller/uploads/product-images")
public class SellerProductImageController {

    private final ProductImageStorageService imageStorageService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignProductImageResponse>> presign(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody PresignProductImageRequest request
    ) {
        PresignProductImageResponse response = imageStorageService.createUpload(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo đường dẫn tải ảnh tạm thời!", response));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<CompleteProductImageResponse>> complete(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CompleteProductImageRequest request
    ) {
        CompleteProductImageResponse response = imageStorageService.completeUpload(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Ảnh sản phẩm đã được xác minh!", response));
    }
}
