package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.product.dto.request.CreateProductReviewRequest;
import com.commercehub.backend.product.dto.request.UpdateProductReviewVisibilityRequest;
import com.commercehub.backend.product.dto.response.ProductReviewResponse;
import com.commercehub.backend.product.service.ProductReviewService;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/product-reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService reviewService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProductReviewResponse>> createReview(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateProductReviewRequest request) {

        ProductReviewResponse response = reviewService.createReview(currentUser.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Cảm ơn bạn đã để lại đánh giá!", response));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductReviewResponse>>> getReviewsByProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<ProductReviewResponse> responses = reviewService.getReviewsByProduct(productId, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách đánh giá thành công!",
                PageResponse.of(responses)
        ));
    }

    @PatchMapping("/{reviewId}/visibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ProductReviewResponse>> setReviewVisibility(
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateProductReviewVisibilityRequest request) {
        ProductReviewResponse response = reviewService.setReviewVisibility(
                reviewId,
                request.getVisible()
        );
        String message = request.getVisible()
                ? "Đã hiển thị lại đánh giá!"
                : "Đã ẩn đánh giá!";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }
}
