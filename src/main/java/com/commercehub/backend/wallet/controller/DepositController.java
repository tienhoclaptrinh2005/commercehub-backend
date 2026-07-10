package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.dto.request.DepositRequest;
//import com.commercehub.backend.payment.service.VnPayService; // Khi bạn code module Payment sẽ gọi sang đây
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet/deposit")
@RequiredArgsConstructor
public class DepositController {

    // private final VnPayService vnPayService;

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createDepositUrl(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody DepositRequest request) {

        // Giả lập luồng: Gọi sang Payment Module để tạo URL VNPay
        // String paymentUrl = vnPayService.createDepositUrl(currentUser.getId(), request.getAmount(), "127.0.0.1");

        String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?..."; // URL giả lập

        return ResponseEntity.ok(ApiResponse.success("Tạo link nạp tiền thành công", paymentUrl));
    }
}