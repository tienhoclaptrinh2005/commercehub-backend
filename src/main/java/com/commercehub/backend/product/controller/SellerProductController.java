package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.UpdateProductRequest;
import com.commercehub.backend.product.dto.request.UploadDigitalAssetRequest;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.dto.response.DigitalAssetImportResponse;
import com.commercehub.backend.product.dto.response.SellerProductListItemResponse;
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
@PreAuthorize("hasRole('SELLER')")
public class SellerProductController {

    private final ProductService productService;
    private final DigitalAssetService digitalAssetService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SellerProductListItemResponse>>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<SellerProductListItemResponse> response = productService.getSellerProducts(
                currentUser.getId(),
                keyword,
                categoryId,
                deliveryType,
                status,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm của gian hàng thành công!", response));
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse response = productService.createProduct(currentUser.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo sản phẩm thành công!", response));
    }


    @PostMapping("/assets/inventory")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<DigitalAssetImportResponse>> uploadAssets(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UploadDigitalAssetRequest request) {

        DigitalAssetImportResponse result = digitalAssetService.uploadAssets(
                currentUser.getUser().getId(), request);

        return ResponseEntity.ok(ApiResponse.success(
                "Đã nạp thành công " + result.addedCount() + " tài khoản!",
                result
        ));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getMyProduct(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long productId) {
        ProductResponse response = productService.getSellerProduct(
                currentUser.getUser().getId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công!", response));
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
            @Valid @RequestBody UpdateProductRequest request) {

        ProductResponse response = productService.updateProduct(currentUser.getUser().getId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công!", response));
    }

}
