package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // GET /api/v1/orders
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success", orderService.getBuyerOrders(currentUser.getId(), pageable)));
    }

    // GET /api/v1/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getOrderDetail(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Success", orderService.getBuyerOrderDetail(currentUser.getId(), id)));
    }

    // GET /api/v1/orders/{id}/assets
    @GetMapping("/{id}/assets")
    public ResponseEntity<ApiResponse<?>> getOrderAssets(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable Long id) {
        // Cần inject DigitalAssetService để query tài sản theo orderId
        return ResponseEntity.ok(ApiResponse.success("Success", null));
    }

    // POST /api/v1/orders/{id}/confirm
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<?>> confirmReceipt(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable Long id) {
        orderService.confirmReceipt(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận nhận hàng", null));
    }

    // POST /api/v1/orders/{id}/auto-complete
    // Thường endpoint system này sẽ được bảo vệ bằng Role SYSTEM hoặc xử lý trực tiếp qua Scheduler
}