package com.commercehub.backend.fee.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.fee.dto.request.*;
import com.commercehub.backend.fee.dto.response.*;
import com.commercehub.backend.fee.service.*;
import com.commercehub.backend.admin.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin quản lý cấu hình tỷ lệ phí sàn.
 * Base path: /api/admin/fee-configs
 *
 * Lưu ý: KHÔNG có chức năng miễn (waive) phí — mọi giao dịch đều chịu phí sàn
 * để duy trì hệ thống.
 */
@RestController
@RequestMapping("/api/v1/admin/fee-configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class FeeConfigController {

    private final PlatformFeeConfigService configService;
    private final PlatformFeeLedgerService ledgerService;
    private final ShopFeeSummaryService summaryService;
    private final AdminAuditService auditService;

    /**
     * GET /api/admin/fee-configs
     * Xem tất cả config phí (lịch sử thay đổi).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FeeConfigResponse>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success(configService.getAllConfigs()));
    }

    /**
     * GET /api/admin/fee-configs/active
     * Xem config đang hoạt động.
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<FeeConfigResponse>> getActiveConfig() {
        return ResponseEntity.ok(ApiResponse.success(configService.getActiveConfig()));
    }

    /**
     * POST /api/admin/fee-configs
     * Tạo config phí mới (deactivate cũ tự động).
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<FeeConfigResponse>> createConfig(
            @Valid @RequestBody CreateFeeConfigRequest request,
            HttpServletRequest http
    ) {
        Long adminId = SecurityUtils.getCurrentUserId();
        FeeConfigResponse created = configService.createConfig(request, adminId);
        auditService.record(adminId, "FEE_CONFIG_CREATED", "FEE_CONFIG", created.getId(), null,
                java.util.Map.of("feeRate", created.getFeeRate()), request.getDescription(), clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Tạo cấu hình phí mới thành công!", created));
    }

    /**
     * PUT /api/admin/fee-configs/change-rate
     * Thay đổi tỷ lệ phí (deactivate cũ + tạo mới trong 1 transaction).
     */
    @PutMapping("/change-rate")
    @Transactional
    public ResponseEntity<ApiResponse<FeeConfigResponse>> changeRate(
            @Valid @RequestBody UpdateFeeConfigRequest request,
            HttpServletRequest http
    ) {
        Long adminId = SecurityUtils.getCurrentUserId();
        FeeConfigResponse previous = configService.getActiveConfig();
        FeeConfigResponse changed = configService.changeRate(request, adminId);
        auditService.record(adminId, "FEE_RATE_CHANGED", "FEE_CONFIG", changed.getId(),
                java.util.Map.of("feeRate", previous.getFeeRate()), java.util.Map.of("feeRate", changed.getFeeRate()),
                request.getChangeReason(), clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Thay đổi tỷ lệ phí thành công!", changed));
    }

    /**
     * GET /api/admin/fee-configs/ledgers?status=PENDING&page=0&size=20
     * Admin xem tất cả ledgers với filter.
     */
    @GetMapping("/ledgers")
    public ResponseEntity<ApiResponse<PageResponse<FeeLedgerResponse>>> getAllLedgers(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by("createdAt").descending());
        Page<FeeLedgerResponse> ledgers = ledgerService.getAllLedgers(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(ledgers)));
    }

    /**
     * GET /api/admin/fee-configs/ledgers/{ledgerId}
     * Admin xem chi tiết 1 ledger bất kỳ.
     */
    @GetMapping("/ledgers/{ledgerId}")
    public ResponseEntity<ApiResponse<FeeLedgerResponse>> getLedgerDetail(@PathVariable Long ledgerId) {
        return ResponseEntity.ok(ApiResponse.success(ledgerService.getLedgerById(ledgerId)));
    }

    /**
     * GET /api/admin/fee-configs/summaries?year=2026&month=1&page=0&size=50
     * Admin xem tổng hợp phí theo tháng của tất cả shops.
     */
    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<PageResponse<ShopFeeSummaryResponse>>> getAllSummaries(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by("totalFee").descending());
        Page<ShopFeeSummaryResponse> summaries = summaryService.getAllSummariesByMonth(
                year,
                month,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(summaries)));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
