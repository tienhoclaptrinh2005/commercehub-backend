package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;
    private final ShopService shopService;

    // Hàm private tiện ích để lấy Shop từ User đang login (qua Service layer)
    private Shop getCurrentSellerShop(Long userId) {
        return shopService.getShopByOwnerId(userId);
    }

    // GET /api/v1/seller/orders
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getSellerOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser, Pageable pageable) {

        Shop shop = getCurrentSellerShop(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Success", orderService.getSellerOrders(shop.getId(), pageable)));
    }

    // GET /api/v1/seller/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getSellerOrderDetail(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable Long id) {

        Shop shop = getCurrentSellerShop(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Success", orderService.getSellerOrderDetail(shop.getId(), id)));
    }
}