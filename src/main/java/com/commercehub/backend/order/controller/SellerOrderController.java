package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.dto.request.DeliverPreOrderRequest;
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.order.service.PreOrderApprovalService; // Import service mới
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.service.ShopService;
import jakarta.validation.Valid;
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
    private final PreOrderApprovalService preOrderApprovalService; // Inject vào đây
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




    //  PRE_ORDER
    // POST /api/v1/seller/orders/{id}/accept
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<?>> acceptPreOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id) {
        preOrderApprovalService.acceptOrder(currentUser.getId(), id);

        return ResponseEntity.ok(ApiResponse.success("Đã duyệt đơn hàng thành công. Vui lòng tiến hành chuẩn bị hàng.", null));
    }

    // POST /api/v1/seller/orders/{id}/reject
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<?>> rejectPreOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @RequestParam(required = false) String reason) { // Nhận lý do từ chối từ Frontend

        preOrderApprovalService.rejectOrder(currentUser.getId(), id, reason);

        return ResponseEntity.ok(ApiResponse.success("Đã từ chối đơn hàng và tự động hoàn tiền cho người mua.", null));
    }

    // POST /api/v1/seller/orders/{id}/complete
    // Body: deliveryContentType (ACCOUNT/KEY/MESSAGE/OTHER) + deliveryContent (bắt buộc)
    // + sellerNotes (ghi chú nội bộ, tùy chọn)
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<?>> completePreOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @RequestBody @Valid DeliverPreOrderRequest request) {

        preOrderApprovalService.completeOrder(currentUser.getId(), id, request);

        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận giao hàng thành công! Tiền sẽ được cộng vào số dư khả dụng sau thời gian đối soát.", null));
    }


    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancelProcessingOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {

        preOrderApprovalService.cancelProcessingOrder(currentUser.getId(), id, reason);

        return ResponseEntity.ok(ApiResponse.success("Đã hủy đơn hàng và tự động hoàn tiền cho người mua.", null));
    }
}