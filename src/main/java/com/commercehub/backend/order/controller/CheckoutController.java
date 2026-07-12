package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.service.CheckoutService; // Đổi import sang CheckoutService
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    // Tiêm (Inject) lớp Điều phối thay vì lớp xử lý trực tiếp
    private final CheckoutService checkoutService;

    // POST /api/v1/checkout
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> processCheckout(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CheckoutRequest request) {

        // Giao toàn quyền quyết định luồng (Instant hay Pre-order) cho CheckoutService
        Long orderId = checkoutService.processCheckout(currentUser.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Checkout thành công", orderId));
    }
}