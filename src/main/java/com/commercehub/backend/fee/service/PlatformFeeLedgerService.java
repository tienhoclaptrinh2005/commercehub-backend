package com.commercehub.backend.fee.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.dto.response.FeeLedgerResponse;
import com.commercehub.backend.fee.entity.PlatformFeeLedger;
import com.commercehub.backend.fee.entity.PlatformFeeLog;
import com.commercehub.backend.fee.mapper.FeeMapper;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
import com.commercehub.backend.fee.repository.PlatformFeeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformFeeLedgerService {

    private final PlatformFeeLedgerRepository ledgerRepository;
    private final PlatformFeeLogRepository logRepository;
    private final ShopFeeSummaryService summaryService;
    private final FeeMapper feeMapper;

    // ======================================================
    // SELLER: Xem lịch sử phí của shop mình
    // ======================================================

    @Transactional(readOnly = true)
    public Page<FeeLedgerResponse> getMyShopFees(Long shopId, Pageable pageable) {
        return ledgerRepository.findByShopIdOrderByCreatedAtDesc(shopId, pageable)
                .map(feeMapper::toFeeLedgerResponse);
    }

    /**
     * Seller xem chi tiết 1 ledger — bắt buộc ledger phải thuộc shop của seller
     * (chống IDOR: seller A không thể đọc phí của shop B).
     */
    @Transactional(readOnly = true)
    public FeeLedgerResponse getLedgerByIdForShop(Long ledgerId, Long shopId) {
        PlatformFeeLedger ledger = ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new AppException(ErrorCode.FEE_LEDGER_NOT_FOUND));

        if (!ledger.getShopId().equals(shopId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return feeMapper.toFeeLedgerResponse(ledger);
    }

    // ======================================================
    // ADMIN: Quản lý toàn bộ ledgers
    // ======================================================

    @Transactional(readOnly = true)
    public FeeLedgerResponse getLedgerById(Long ledgerId) {
        return feeMapper.toFeeLedgerResponse(
                ledgerRepository.findById(ledgerId)
                        .orElseThrow(() -> new AppException(ErrorCode.FEE_LEDGER_NOT_FOUND))
        );
    }

    @Transactional(readOnly = true)
    public Page<FeeLedgerResponse> getAllLedgers(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return ledgerRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(feeMapper::toFeeLedgerResponse);
        }
        return ledgerRepository.findAll(pageable).map(feeMapper::toFeeLedgerResponse);
    }

    // ======================================================
    // INTERNAL: Được gọi bởi HoldReleaseProcessor khi release
    // ======================================================

    /**
     * Đánh dấu ledger COLLECTED sau khi HoldReleaseProcessor giải phóng tiền.
     * Dùng pessimistic lock để không race với markAsCancelled (dispute).
     * Chạy CHUNG transaction với processor — nếu wallet rollback thì ledger/summary cũng rollback.
     */
    @Transactional
    public void markAsCollected(Long ledgerId) {
        PlatformFeeLedger ledger = ledgerRepository.findByIdWithLock(ledgerId)
                .orElseThrow(() -> new AppException(ErrorCode.FEE_LEDGER_NOT_FOUND));

        if (!"PENDING".equals(ledger.getStatus())) {
            log.warn("Ledger ID={} không ở trạng thái PENDING (hiện: {}), bỏ qua.", ledgerId, ledger.getStatus());
            return;
        }

        ledger.setStatus("COLLECTED");
        ledger.setCollectedAt(OffsetDateTime.now());
        ledgerRepository.save(ledger);

        // Audit log — changedBy null = System
        appendLog(ledgerId, "PENDING", "COLLECTED", ledger.getFeeAmount(), null, "Thu phí T+7 tự động");

        // Cập nhật tổng hợp tháng (cùng transaction — commit/rollback đồng bộ với wallet)
        summaryService.applyCollect(
                ledger.getShopId(), ledger.getFeeIncurredAt(),
                ledger.getSaleAmount(), ledger.getFeeAmount(), ledger.getSellerNetAmount()
        );
    }

    /**
     * Hủy ledger khi đơn bị hoàn tiền/dispute buyer thắng.
     * CHỈ được hủy khi còn PENDING — ledger đã COLLECTED nghĩa là tiền phí đã
     * chuyển vào ví platform, muốn đảo phải có flow reversal riêng.
     */
    @Transactional
    public void markAsCancelled(Long feeLedgerId, String reason, Long changedBy) {
        PlatformFeeLedger ledger = ledgerRepository.findByIdWithLock(feeLedgerId)
                .orElseThrow(() -> new AppException(ErrorCode.FEE_LEDGER_NOT_FOUND));

        if (!"PENDING".equals(ledger.getStatus())) {
            throw new AppException(ErrorCode.FEE_LEDGER_ALREADY_PROCESSED);
        }

        ledger.setStatus("CANCELLED");
        ledger.setCancelledAt(OffsetDateTime.now());
        ledgerRepository.save(ledger);

        appendLog(feeLedgerId, "PENDING", "CANCELLED", ledger.getFeeAmount(), changedBy,
                reason != null ? reason : "Buyer thắng dispute - hoàn tiền");

        summaryService.applyRefund(
                ledger.getShopId(), ledger.getFeeIncurredAt(),
                ledger.getSaleAmount(), ledger.getFeeAmount(), ledger.getSellerNetAmount()
        );
    }


    // ======================================================
    // PRIVATE HELPER
    // ======================================================

    private void appendLog(Long ledgerId, String from, String to,
                           java.math.BigDecimal feeAmount, Long changedBy, String reason) {
        PlatformFeeLog log = PlatformFeeLog.builder()
                .feeLedgerId(ledgerId)
                .fromStatus(from)
                .toStatus(to)
                .feeAmount(feeAmount)
                .changedBy(changedBy)
                .reason(reason)
                .build();
        logRepository.save(log);
    }
}
