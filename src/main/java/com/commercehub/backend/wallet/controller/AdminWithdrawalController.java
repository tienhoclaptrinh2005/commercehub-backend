package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.admin.dto.AdminRequests;
import com.commercehub.backend.admin.service.AdminCommandService;
import jakarta.servlet.http.HttpServletRequest;
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
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminWithdrawalController {

    private final AdminCommandService adminCommandService;

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
            @RequestParam(required = false) String note,
            HttpServletRequest http) {

        adminCommandService.processWithdrawal(currentUser.getId(), id,
                new AdminRequests.WithdrawalDecision(action, note),
                clientIp(http), http.getHeader("User-Agent"));

        String message = "APPROVE".equalsIgnoreCase(action)
                ? "Đã duyệt đơn rút tiền thành công."
                : "Đã từ chối đơn rút tiền. Tiền đã hoàn lại cho người dùng.";

        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
