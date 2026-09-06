package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.dto.request.DepositRequest;
import com.commercehub.backend.wallet.dto.request.SePayIpnRequest;
import com.commercehub.backend.wallet.dto.response.DepositResponse;
import com.commercehub.backend.wallet.dto.response.SePayCheckoutResponse;
import com.commercehub.backend.wallet.service.DepositService;
import com.commercehub.backend.wallet.service.SePayGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/wallet/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;
    private final SePayGatewayService sePayGatewayService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DepositResponse>>> getDepositHistory(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<DepositResponse> history = depositService.getMyDeposits(currentUser.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử nạp tiền thành công", history));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SePayCheckoutResponse>> createDepositCheckout(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody DepositRequest request) {

        String txCode = "SEPAY_" + UUID.randomUUID().toString().replace("-", "");

        SePayCheckoutResponse checkout = sePayGatewayService.createCheckout(
                currentUser.getId(),
                request.getAmount(),
                txCode
        );
        depositService.createPendingDeposit(currentUser.getId(), request.getAmount(), txCode);

        return ResponseEntity.ok(ApiResponse.success("Tạo phiên thanh toán SePay thành công", checkout));
    }

    @PostMapping("/sepay-ipn")
    public ResponseEntity<ApiResponse<Void>> sePayIpnCallback(
            @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
            @Valid @RequestBody SePayIpnRequest request) {
        sePayGatewayService.processIpn(secretKey, request);
        log.info("SePay IPN processed - invoice: {}, type: {}",
                request.getOrder().getOrderInvoiceNumber(),
                request.getNotificationType());
        return ResponseEntity.ok(ApiResponse.success("IPN processed", null));
    }
}
