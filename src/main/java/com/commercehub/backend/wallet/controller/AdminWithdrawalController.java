package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ĐÃ FIX Bug #33: Controller dành cho Admin duyệt/từ chối yêu cầu rút tiền.
 * Trước đó WithdrawalService.processWithdrawal là dead code — không ai gọi.
 */
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;

    /**
     * Admin duyệt hoặc từ chối đơn rút tiền.
     *
     * @param id     ID của Withdrawal cần xử lý
     * @param action "APPROVE" hoặc "REJECT"
     * @param note   Ghi chú của admin (tùy chọn)
     */
    @PutMapping("/{id}/process")
    public ResponseEntity<ApiResponse<Void>> processWithdrawal(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @RequestParam String action,
            @RequestParam(required = false) String note) {

        withdrawalService.processWithdrawal(id, currentUser.getId(), action, note);

        String message = "APPROVE".equalsIgnoreCase(action)
                ? "Đã duyệt đơn rút tiền thành công."
                : "Đã từ chối đơn rút tiền. Tiền đã hoàn lại cho người dùng.";

        return ResponseEntity.ok(ApiResponse.success(message, null));
    }
}
