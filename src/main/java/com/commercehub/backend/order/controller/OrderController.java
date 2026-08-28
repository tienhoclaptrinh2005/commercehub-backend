package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.order.service.PreOrderApprovalService;
import com.commercehub.backend.product.service.DigitalAssetService;
import com.commercehub.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;
    private final DigitalAssetService digitalAssetService;
    private final PreOrderApprovalService preOrderApprovalService;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false)
            @Size(max = 50, message = "Mã đơn hàng tối đa 50 ký tự") String orderCode,
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
        return ResponseEntity.ok(ApiResponse.success(
                "Success",
                orderService.getBuyerOrders(
                        currentUser.getId(),
                        orderCode,
                        status,
                        fromDate,
                        toDate,
                        beforePlacedAt,
                        beforeId,
                        size
                )
        ));
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
