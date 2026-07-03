package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.UpdateProductRequest;
import com.commercehub.backend.product.dto.request.UploadDigitalAssetRequest;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.service.DigitalAssetService;
import com.commercehub.backend.product.service.ProductService;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;
    private final DigitalAssetService digitalAssetService;

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse response = productService.createProduct(currentUser.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công!", response));
    }


    @PostMapping("/products/assets/inventory")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Integer>> uploadAssets(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UploadDigitalAssetRequest request) {

        int addedCount = digitalAssetService.uploadAssets(currentUser.getUser().getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Đã nạp thành công " + addedCount + " tài khoản!", addedCount));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long productId) {

        productService.deleteProduct(currentUser.getUser().getId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa sản phẩm thành công!", null));
    }


    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long productId,
            @RequestBody UpdateProductRequest request) {

        ProductResponse response = productService.updateProduct(currentUser.getUser().getId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công!", response));
    }

}