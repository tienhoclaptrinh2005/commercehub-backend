package com.commercehub.backend.cart.controller;

import com.commercehub.backend.cart.dto.request.AddToCartRequest;
import com.commercehub.backend.cart.dto.request.CartCheckoutRequest;
import com.commercehub.backend.cart.dto.request.UpdateCartItemRequest;
import com.commercehub.backend.cart.dto.response.CartResponse;
import com.commercehub.backend.cart.service.CartService;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.dto.response.CheckoutOrderResponse;
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Giỏ hàng của user đang đăng nhập.
 * Base path: /api/v1/cart
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final OrderService orderService;

    /** GET /api/v1/cart — xem giỏ hàng */
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getMyCart(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getMyCart(currentUser.getId())));
    }

    /** POST /api/v1/cart/items — thêm sản phẩm vào giỏ (trùng variant thì cộng dồn) */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã thêm vào giỏ hàng", cartService.addItem(currentUser.getId(), request)));
    }

    /** PUT /api/v1/cart/items/{itemId} — đổi số lượng */
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.updateItemQuantity(currentUser.getId(), itemId, request)));
    }

    /** DELETE /api/v1/cart/items/{itemId} — xóa 1 dòng khỏi giỏ */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xóa khỏi giỏ hàng", cartService.removeItem(currentUser.getId(), itemId)));
    }

    /** DELETE /api/v1/cart — xóa sạch giỏ */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        cartService.clearCart(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã xóa toàn bộ giỏ hàng", null));
    }

    /**
     * POST /api/v1/cart/checkout — thanh toán toàn bộ giỏ.
     * Tự tách đơn theo shop/loại giao hàng, trừ ví, xóa giỏ khi thành công.
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<List<CheckoutOrderResponse>>> checkout(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody @Valid CartCheckoutRequest request) {
        List<Long> orderIds = cartService.checkoutCart(currentUser.getId(), request);
        List<CheckoutOrderResponse> orders =
                orderService.getBuyerCheckoutOrders(currentUser.getId(), orderIds);
        return ResponseEntity.ok(ApiResponse.success("Checkout giỏ hàng thành công", orders));
    }
}
