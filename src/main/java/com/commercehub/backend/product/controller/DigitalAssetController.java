package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.product.dto.response.DigitalAssetResponse;
import com.commercehub.backend.product.service.DigitalAssetService;
import com.commercehub.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/v1/seller/digital-assets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class DigitalAssetController {

    private final DigitalAssetService digitalAssetService;

    // 1. Xem danh sách tài khoản trong kho của 1 gói cụ thể
    @GetMapping("/variant/{variantId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Page<DigitalAssetResponse>>> getAssetsByVariant(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Gọi Service lấy danh sách (Nhớ check quyền: chỉ chủ shop mới xem được kho của mình)
        Page<DigitalAssetResponse> assets = digitalAssetService.getAssetsByVariant(
                currentUser.getUser().getId(), variantId, page, size);

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho thành công!", assets));
    }

    // 2. Xóa một tài khoản khỏi kho (Chỉ cho phép xóa nếu status = AVAILABLE)
    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long assetId) {

        digitalAssetService.deleteAsset(currentUser.getUser().getId(), assetId);

        return ResponseEntity.ok(ApiResponse.success("Đã xóa tài khoản khỏi kho!", null));
    }
}
