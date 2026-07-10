package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.dto.request.WithdrawalRequest;
import com.commercehub.backend.wallet.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet/withdraw")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> requestWithdrawal(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody WithdrawalRequest request) {

        withdrawalService.requestWithdrawal(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Gửi yêu cầu rút tiền thành công. Vui lòng chờ hệ thống duyệt.", null));
    }
}