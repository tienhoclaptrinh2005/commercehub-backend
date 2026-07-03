package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.product.dto.request.CreateVariantRequest;
import com.commercehub.backend.product.dto.request.UpdateVariantRequest;
import com.commercehub.backend.product.dto.response.ProductVariantResponse;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.service.ProductVariantService;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService variantService;
    private final ProductMapper productMapper; // Tận dụng Mapper vừa tạo

    @PostMapping("/seller/product-variants")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> createVariant(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateVariantRequest request) {

        ProductVariant variant = variantService.createVariant(currentUser.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Thêm gói sản phẩm thành công!", productMapper.toVariantResponse(variant)));
    }

    @PutMapping("/seller/product-variants/{variantId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariant(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateVariantRequest request) {

        ProductVariant variant = variantService.updateVariant(currentUser.getUser().getId(), variantId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật gói sản phẩm thành công!", productMapper.toVariantResponse(variant)));
    }

    @GetMapping("/products/{productId}/variants")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getVariantsByProduct(@PathVariable Long productId) {

        List<ProductVariant> variants = variantService.getVariantsByProduct(productId);

        // Code ngắn gọn và đẹp hơn rất nhiều
        List<ProductVariantResponse> responses = variants.stream()
                .map(productMapper::toVariantResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách biến thể thành công!", responses));
    }
}