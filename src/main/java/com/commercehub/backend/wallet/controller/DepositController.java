package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.dto.request.DepositRequest;
import com.commercehub.backend.wallet.dto.response.DepositQrResponse;
import com.commercehub.backend.wallet.dto.response.DepositResponse;
import com.commercehub.backend.wallet.service.DepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet/deposits")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DepositResponse>>> getDepositHistory(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<DepositResponse> history = depositService.getMyDeposits(currentUser.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử nạp tiền thành công", history));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepositQrResponse>> createDeposit(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody DepositRequest request) {
        DepositQrResponse deposit = depositService.createDeposit(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Tạo mã QR nạp tiền thành công", deposit));
    }

    @GetMapping("/{transactionCode}")
    public ResponseEntity<ApiResponse<DepositQrResponse>> getDepositStatus(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable String transactionCode) {
        DepositQrResponse deposit = depositService.getMyDepositStatus(currentUser.getId(), transactionCode);
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái nạp tiền thành công", deposit));
    }
}
