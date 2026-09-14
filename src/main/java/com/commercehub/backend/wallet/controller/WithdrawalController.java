package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.dto.request.WithdrawalRequest;
import com.commercehub.backend.wallet.dto.response.WithdrawalResponse;
import com.commercehub.backend.wallet.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller/wallet/withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody WithdrawalRequest request) {

        WithdrawalResponse response = withdrawalService.requestWithdrawal(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(
                "Gửi yêu cầu rút tiền thành công. Vui lòng chờ Admin kiểm tra.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> getMyWithdrawals(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy lịch sử rút tiền thành công.",
                withdrawalService.getMyWithdrawals(currentUser.getId(), page, size)));
    }
}
