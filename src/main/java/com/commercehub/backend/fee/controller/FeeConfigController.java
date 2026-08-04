package com.commercehub.backend.fee.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.fee.dto.request.*;
import com.commercehub.backend.fee.dto.response.*;
import com.commercehub.backend.fee.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/fee-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FeeConfigController {

    private final PlatformFeeConfigService configService;
    private final PlatformFeeLedgerService ledgerService;
    private final ShopFeeSummaryService summaryService;

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
    public ResponseEntity<ApiResponse<FeeConfigResponse>> createConfig(
            @Valid @RequestBody CreateFeeConfigRequest request
    ) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Tạo cấu hình phí mới thành công!", configService.createConfig(request, adminId)
        ));
    }

    /**
     * PUT /api/admin/fee-configs/change-rate
     * Thay đổi tỷ lệ phí (deactivate cũ + tạo mới trong 1 transaction).
     */
    @PutMapping("/change-rate")
    public ResponseEntity<ApiResponse<FeeConfigResponse>> changeRate(
            @Valid @RequestBody UpdateFeeConfigRequest request
    ) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Thay đổi tỷ lệ phí thành công!", configService.changeRate(request, adminId)
        ));
    }

    /**
     * GET /api/admin/fee-configs/ledgers?status=PENDING&page=0&size=20
     * Admin xem tất cả ledgers với filter.
     */
    @GetMapping("/ledgers")
    public ResponseEntity<ApiResponse<Page<FeeLedgerResponse>>> getAllLedgers(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(ledgerService.getAllLedgers(status, pageable)));
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
    public ResponseEntity<ApiResponse<Page<ShopFeeSummaryResponse>>> getAllSummaries(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("totalFee").descending());
        return ResponseEntity.ok(ApiResponse.success(summaryService.getAllSummariesByMonth(year, month, pageable)));
    }
}
