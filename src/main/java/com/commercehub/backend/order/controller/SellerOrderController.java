package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.request.DeliverPreOrderRequest;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.order.service.PreOrderApprovalService;
import com.commercehub.backend.product.service.DigitalAssetService;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.service.ShopService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/seller/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
@Validated
public class SellerOrderController {

    private final OrderService orderService;
    private final ShopService shopService;
    private final PreOrderApprovalService preOrderApprovalService;
    private final DigitalAssetService digitalAssetService;

    private Shop getCurrentSellerShop(Long userId) {
        return shopService.getShopByOwnerId(userId);
    }

    // GET /api/v1/seller/orders
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getSellerOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false)
            @Size(max = 100, message = "Từ khóa tìm kiếm tối đa 100 ký tự") String search,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime beforePlacedAt,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Số đơn mỗi lần tải tối thiểu là 1")
            @Max(value = 50, message = "Số đơn mỗi lần tải tối đa là 50") int size) {

        Shop shop = getCurrentSellerShop(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Success",
                orderService.getSellerOrders(
                        shop.getId(), search, deliveryType, status, fromDate, toDate,
                        beforePlacedAt, beforeId, size
                )
        ));
    }

    // GET /api/v1/seller/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getSellerOrderDetail(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable @Min(value = 1, message = "ID đơn hàng không hợp lệ") Long id) {

        Shop shop = getCurrentSellerShop(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Success", orderService.getSellerOrderDetail(shop.getId(), id)));
    }

    // Chỉ trả nội dung tài khoản đã giao cho đúng shop sở hữu đơn INSTANT.
    @GetMapping("/{id}/assets")
    public ResponseEntity<ApiResponse<?>> getSellerOrderAssets(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable @Min(value = 1, message = "ID đơn hàng không hợp lệ") Long id) {

        Shop shop = getCurrentSellerShop(currentUser.getId());
        Order order = orderService.getSellerOrderOrThrow(shop.getId(), id);
        if (!"INSTANT".equals(order.getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin tài khoản đã giao thành công",
                digitalAssetService.getDeliveredAssetsByOrderId(order.getId())
        ));
    }

    //  PRE_ORDER
    // POST /api/v1/seller/orders/{id}/accept
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<?>> acceptPreOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable @Min(value = 1, message = "ID đơn hàng không hợp lệ") Long id) {
        preOrderApprovalService.acceptOrder(currentUser.getId(), id);

        return ResponseEntity.ok(ApiResponse.success("Đã duyệt đơn hàng thành công. Vui lòng tiến hành chuẩn bị hàng.", null));
    }

    // POST /api/v1/seller/orders/{id}/reject
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<?>> rejectPreOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable @Min(value = 1, message = "ID đơn hàng không hợp lệ") Long id,
            @RequestParam(required = false)
            @Size(max = 500, message = "Lý do từ chối tối đa 500 ký tự") String reason) {

        preOrderApprovalService.rejectOrder(currentUser.getId(), id, reason);

        return ResponseEntity.ok(ApiResponse.success("Đã từ chối đơn hàng và tự động hoàn tiền cho người mua.", null));
    }

    // POST /api/v1/seller/orders/{id}/complete
    // Body: deliveryContentType (ACCOUNT/KEY/MESSAGE/OTHER) + deliveryContent (bắt buộc)
    // + sellerNotes (ghi chú nội bộ, tùy chọn)
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<?>> completePreOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable @Min(value = 1, message = "ID đơn hàng không hợp lệ") Long id,
            @RequestBody @Valid DeliverPreOrderRequest request) {

        preOrderApprovalService.completeOrder(currentUser.getId(), id, request);

        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận giao hàng thành công! Tiền sẽ được cộng vào số dư khả dụng sau thời gian đối soát.", null));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancelProcessingOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable @Min(value = 1, message = "ID đơn hàng không hợp lệ") Long id,
            @RequestParam(required = false)
            @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự") String reason) {

        preOrderApprovalService.cancelProcessingOrder(currentUser.getId(), id, reason);

        return ResponseEntity.ok(ApiResponse.success("Đã hủy đơn hàng và tự động hoàn tiền cho người mua.", null));
    }
}
