package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.product.dto.response.ProductImageResponse;
import com.commercehub.backend.product.service.ProductImageService;
import com.commercehub.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService imageService;

    @PostMapping("/seller/products/{productId}/images")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> addImages(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long productId,
            @RequestBody List<String> imageUrls) {

        List<ProductImageResponse> responses = imageService.addImages(currentUser.getUser().getId(), productId, imageUrls);
        return ResponseEntity.ok(ApiResponse.success("Thêm ảnh thành công!", responses));
    }

    @DeleteMapping("/seller/products/images/{imageId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long imageId) {

        imageService.deleteImage(currentUser.getUser().getId(), imageId);
        return ResponseEntity.ok(ApiResponse.success("Xóa ảnh thành công!", null));
    }

    @GetMapping("/products/{productId}/images")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImages(@PathVariable Long productId) {

        List<ProductImageResponse> responses = imageService.getImagesByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ảnh thành công!", responses));
    }
}