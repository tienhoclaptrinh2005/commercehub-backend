package com.commercehub.backend.order.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.dto.response.CheckoutOrderResponse;
import com.commercehub.backend.order.service.CheckoutService; // Đổi import sang CheckoutService
import com.commercehub.backend.order.service.OrderService;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    // Tiêm (Inject) lớp Điều phối thay vì lớp xử lý trực tiếp
    private final CheckoutService checkoutService;
    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<List<CheckoutOrderResponse>>> checkout(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody @Valid CheckoutRequest request) {

        // CheckoutService dùng ID nội bộ; response công khai chỉ trả mã đơn hàng.
        List<Long> orderIds = checkoutService.processCheckout(currentUser.getId(), request);

        List<CheckoutOrderResponse> orders =
                orderService.getBuyerCheckoutOrders(currentUser.getId(), orderIds);
        return ResponseEntity.ok(ApiResponse.success("Checkout thành công", orders));
    }
}
