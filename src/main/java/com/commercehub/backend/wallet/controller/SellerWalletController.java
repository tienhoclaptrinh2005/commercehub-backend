package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.SliceResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.dto.response.WalletTransactionResponse;
import com.commercehub.backend.wallet.service.WalletTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/wallet")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerWalletController {

    private final WalletTransactionService walletTransactionService;

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<SliceResponse<WalletTransactionResponse>>> getTransactionHistory(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String category
    ) {
        SliceResponse<WalletTransactionResponse> transactions =
                walletTransactionService.getSellerTransactions(
                        currentUser.getId(), page, size, category);

        return ResponseEntity.ok(
                ApiResponse.success("Lấy lịch sử tài chính người bán thành công", transactions));
    }
}
