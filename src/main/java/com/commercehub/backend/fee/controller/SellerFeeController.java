package com.commercehub.backend.fee.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.fee.dto.response.*;
import com.commercehub.backend.fee.service.*;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Seller xem lịch sử phí sàn của shop mình.
 * Base path: /api/v1/seller/fees
 *
 * shopId LUÔN được suy ra từ user đang đăng nhập (SecurityContext) —
 * seller không thể truyền shopId của shop khác để đọc trộm dữ liệu.
 */
@RestController
@RequestMapping("/api/v1/seller/fees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerFeeController {

    private final PlatformFeeLedgerService ledgerService;
    private final ShopFeeSummaryService summaryService;
    private final PlatformFeeConfigService configService;
    private final ShopService shopService;

    /**
     * GET /api/v1/seller/fees/current-config
     * Seller xem cấu hình phí sàn đang thực sự được dùng để tính cho đơn mới.
     */
    @GetMapping("/current-config")
    public ResponseEntity<ApiResponse<FeeConfigResponse>> getCurrentConfig() {
        return ResponseEntity.ok(ApiResponse.success(configService.getActiveConfig()));
    }

    /**
     * GET /api/v1/seller/fees?page=0&size=20
     * Seller xem danh sách phí đã/chưa thu của shop mình.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FeeLedgerResponse>>> getMyFees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Shop myShop = shopService.getShopByOwnerId(SecurityUtils.getCurrentUserId());
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by("createdAt").descending());
        Page<FeeLedgerResponse> fees = ledgerService.getMyShopFees(myShop.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(fees)));
    }

    /**
     * GET /api/v1/seller/fees/summary?year=2026&month=1
     * Xem tổng hợp phí theo tháng của shop mình.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ShopFeeSummaryResponse>> getMonthlySummary(
            @RequestParam int year,
            @RequestParam int month
    ) {
        Shop myShop = shopService.getShopByOwnerId(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(summaryService.getSummary(myShop.getId(), year, month)));
    }

    /**
     * GET /api/v1/seller/fees/{ledgerId}
     * Xem chi tiết 1 bản ghi phí — chỉ khi ledger thuộc shop của mình.
     */
    @GetMapping("/{ledgerId}")
    public ResponseEntity<ApiResponse<FeeLedgerResponse>> getLedgerDetail(
            @PathVariable Long ledgerId
    ) {
        Shop myShop = shopService.getShopByOwnerId(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(
                ledgerService.getLedgerByIdForShop(ledgerId, myShop.getId())
        ));
    }
}
