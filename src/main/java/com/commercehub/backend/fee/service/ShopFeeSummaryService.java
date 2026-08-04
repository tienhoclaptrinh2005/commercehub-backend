package com.commercehub.backend.fee.service;

import com.commercehub.backend.fee.dto.response.ShopFeeSummaryResponse;
import com.commercehub.backend.fee.entity.ShopFeeSummary;
import com.commercehub.backend.fee.mapper.FeeMapper;
import com.commercehub.backend.fee.repository.ShopFeeSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopFeeSummaryService {

    private final ShopFeeSummaryRepository summaryRepository;
    private final FeeMapper feeMapper;

    @Transactional(readOnly = true)
    public ShopFeeSummaryResponse getSummary(Long shopId, int year, int month) {
        return summaryRepository.findByShopIdAndPeriodYearAndPeriodMonth(shopId, year, month)
                .map(feeMapper::toShopFeeSummaryResponse)
                .orElse(ShopFeeSummaryResponse.builder()
                        .shopId(shopId).periodYear(year).periodMonth(month)
                        .totalSales(BigDecimal.ZERO).totalFee(BigDecimal.ZERO)
                        .totalNet(BigDecimal.ZERO)
                        .totalRefunded(BigDecimal.ZERO).orderCount(0).disputeCount(0)
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<ShopFeeSummaryResponse> getAllSummariesByMonth(int year, int month, Pageable pageable) {
        return summaryRepository
                .findByPeriodYearAndPeriodMonthOrderByTotalFeeDesc(year, month, pageable)
                .map(feeMapper::toShopFeeSummaryResponse);
    }

    /**
     * Cập nhật summary khi HoldReleaseProcessor thu phí thành công.
     * Chạy CHUNG transaction với caller: nếu wallet/ledger rollback thì summary
     * cũng rollback — không bao giờ lệch sổ.
     * Dùng pessimistic lock trên row summary để chống lost update khi 2 hold
     * release của cùng shop/tháng chạy song song.
     */
    @Transactional
    public void applyCollect(Long shopId, OffsetDateTime feeIncurredAt,
                             BigDecimal saleAmount, BigDecimal feeAmount, BigDecimal netAmount) {
        ShopFeeSummary summary = getOrCreateForUpdate(shopId, feeIncurredAt);
        summary.setTotalSales(summary.getTotalSales().add(saleAmount));
        summary.setTotalFee(summary.getTotalFee().add(feeAmount));
        summary.setTotalNet(summary.getTotalNet().add(netAmount));
        summary.setOrderCount(summary.getOrderCount() + 1);
        summaryRepository.save(summary);
    }

    /**
     * Cập nhật summary khi ledger PENDING bị hủy (đơn hoàn tiền / buyer thắng dispute).
     * Lưu ý: ledger chưa từng COLLECTED nên phí CHƯA được cộng vào totalFee
     * → chỉ ghi nhận doanh số hoàn và số vụ khiếu nại, không trừ totalFee.
     */
    @Transactional
    public void applyRefund(Long shopId, OffsetDateTime feeIncurredAt,
                            BigDecimal saleAmount, BigDecimal feeAmount, BigDecimal netAmount) {
        ShopFeeSummary summary = getOrCreateForUpdate(shopId, feeIncurredAt);
        summary.setTotalRefunded(summary.getTotalRefunded().add(saleAmount));
        summary.setDisputeCount(summary.getDisputeCount() + 1);
        summaryRepository.save(summary);
    }

    // ======================================================
    // PRIVATE
    // ======================================================

    private ShopFeeSummary getOrCreateForUpdate(Long shopId, OffsetDateTime date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        return summaryRepository
                .findByShopIdAndPeriodYearAndPeriodMonthForUpdate(shopId, year, month)
                .orElseGet(() -> ShopFeeSummary.builder()
                        .shopId(shopId)
                        .periodYear(year)
                        .periodMonth(month)
                        .totalSales(BigDecimal.ZERO)
                        .totalFee(BigDecimal.ZERO)
                        .totalNet(BigDecimal.ZERO)
                        .totalRefunded(BigDecimal.ZERO)
                        .orderCount(0)
                        .disputeCount(0)
                        .build());
    }
}
