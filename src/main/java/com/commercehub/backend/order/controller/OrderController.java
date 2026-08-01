package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.order.service.PreOrderApprovalService;
import com.commercehub.backend.product.service.DigitalAssetService;
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
    private final DigitalAssetService digitalAssetService;
    private final PreOrderApprovalService preOrderApprovalService;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success", orderService.getBuyerOrders(currentUser.getId(), pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getOrderDetail(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Success", orderService.getBuyerOrderDetail(currentUser.getId(), id)));
    }

    @GetMapping("/{id}/assets")
    public ResponseEntity<ApiResponse<?>> getOrderAssets(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable Long id) {

        Order order = orderService.getBuyerOrderOrThrow(currentUser.getId(), id);
        var assets = digitalAssetService.getDeliveredAssetsByOrderId(order.getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tài khoản thành công", assets));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id) {
        preOrderApprovalService.cancelOrderByBuyer(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy đơn hàng và hoàn tiền thành công.", null));
    }
}
