package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.service.PlatformFeeLedgerService;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldReleaseService {

    private final HoldReleaseRepository holdReleaseRepository;
    private final HoldReleaseProcessor holdReleaseProcessor;
    private final PlatformFeeLedgerService platformFeeLedgerService;
    private final WalletService walletService;

    public void processDueReleases(OffsetDateTime now) {
        List<HoldRelease> dueReleases = holdReleaseRepository.findDueReleases(now);

        for (HoldRelease hr : dueReleases) {
            try {
                holdReleaseProcessor.processSingle(hr);
            } catch (Exception e) {
                log.error("Lỗi khi xử lý giải phóng tiền HoldRelease ID {}: {}", hr.getId(), e.getMessage());
            }
        }
    }

    // =========================================================
    // LOGIC KHIẾU NẠI (DISPUTE) BẢO VỆ NGƯỜI MUA
    // =========================================================

    /**
     * Đóng băng khoản giữ tiền của 1 OrderItem khi Buyer khiếu nại.
     * Dùng PESSIMISTIC LOCK để không race với HoldReleaseProcessor:
     * hoặc freeze thắng (scheduler thấy DISPUTED sẽ bỏ qua),
     * hoặc scheduler thắng (freeze thấy RELEASED sẽ báo lỗi trạng thái).
     */
    @Transactional
    public void freezeForDispute(Long orderItemId) {
        HoldRelease hr = holdReleaseRepository.findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(() -> new AppException(ErrorCode.HOLD_RELEASE_NOT_FOUND));

        if (!"HOLDING".equals(hr.getStatus())) {
            throw new AppException(ErrorCode.HOLD_RELEASE_INVALID_STATUS);
        }

        hr.setStatus("DISPUTED");
        hr.setComplainedAt(OffsetDateTime.now());
        holdReleaseRepository.save(hr);

        log.info("Đã ĐÓNG BĂNG số tiền của OrderItem ID {} do có khiếu nại từ người mua.", orderItemId);
    }

    /**
     * Admin phán xử tranh chấp.
     * Dùng PESSIMISTIC LOCK — chỉ xử lý được khi đang DISPUTED (terminal-safe).
     */
    @Transactional
    public void resolveDispute(Long holdReleaseId, boolean isBuyerWin, Long buyerId, Long sellerId, Long orderId) {

        HoldRelease hr = holdReleaseRepository.findByIdWithLock(holdReleaseId)
                .orElseThrow(() -> new AppException(ErrorCode.HOLD_RELEASE_NOT_FOUND));

        if (!"DISPUTED".equals(hr.getStatus())) {
            throw new AppException(ErrorCode.HOLD_RELEASE_NOT_DISPUTED);
        }

        if (isBuyerWin) {

            // KỊCH BẢN 1: BUYER THẮNG KIỆN
            hr.setStatus("REFUNDED");

            // BƯỚC 1: Gỡ phong tỏa tiền trong ví của Seller (Hủy Hold).
            // refId = hr.getId() để mỗi HoldRelease chỉ hoàn đúng 1 lần
            // (1 đơn có thể có nhiều item tranh chấp riêng biệt).
            walletService.cancelHoldForSeller(sellerId, hr.getHoldAmount(), hr.getId());

            // BƯỚC 2: Hoàn lại tiền vào ví khả dụng cho Buyer
            walletService.addBalance(
                    buyerId,
                    hr.getHoldAmount(),
                    "DISPUTE_REFUND",
                    hr.getId(),
                    "HOLD_RELEASE"
            );

            // BƯỚC 3: Hủy hóa đơn thu phí sàn (giao dịch thất bại, sàn không thu phí)
            if (hr.getFeeLedgerId() != null) {
                platformFeeLedgerService.markAsCancelled(
                        hr.getFeeLedgerId(),
                        "Buyer thắng dispute - Order ID " + orderId,
                        null
                );
            }

            log.info("Tranh chấp ID {}: BUYER thắng. Đã hoàn {} cho Buyer ID {}", holdReleaseId, hr.getHoldAmount(), buyerId);

        } else {
            // KỊCH BẢN 2: SELLER THẮNG KIỆN
            // Trả lại trạng thái HOLDING để scheduler tiếp tục đếm ngày và tự nhả tiền cho Seller
            hr.setStatus("HOLDING");
            log.info("Tranh chấp ID {}: SELLER thắng. Tiếp tục giam tiền chờ nhả tự động.", holdReleaseId);
        }

        holdReleaseRepository.save(hr);
    }
}
