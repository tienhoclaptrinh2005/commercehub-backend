package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.product.dto.request.CreatePreOrderConfigRequest;
import com.commercehub.backend.product.dto.response.PreOrderConfigResponse;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.service.PreOrderConfigService;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller/pre-order-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class PreOrderConfigController {

    private final PreOrderConfigService preOrderConfigService;
    private final ProductMapper productMapper;

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<PreOrderConfigResponse>> createOrUpdatePreOrderConfig(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreatePreOrderConfigRequest request) {

        var config = preOrderConfigService.createOrUpdateConfig(currentUser.getUser().getId(), request);

        return ResponseEntity.ok(ApiResponse.success(
                "Lưu cấu hình Đặt trước thành công!",
                productMapper.toPreOrderConfigResponse(config)
        ));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<PreOrderConfigResponse>> getConfig(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long productId) {

        var config = preOrderConfigService.getConfigByProductId(
                currentUser.getUser().getId(),
                productId
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin cấu hình thành công!",
                productMapper.toPreOrderConfigResponse(config)
        ));
    }
}
