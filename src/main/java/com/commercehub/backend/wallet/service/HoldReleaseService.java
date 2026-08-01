package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
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
    private final PlatformFeeLedgerRepository feeLedgerRepository;
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


    // LOGIC KHIẾU NẠI (DISPUTE) BẢO VỆ NGƯỜI MUA

    @Transactional
    public void freezeForDispute(Long orderItemId) {
        HoldRelease hr = holdReleaseRepository.findByOrderItemId(orderItemId);
        if (hr == null) {
            throw new AppException(ErrorCode.RECORD_NOT_FOUND);
        }

        if (!"HOLDING".equals(hr.getStatus())) {
            throw new AppException(ErrorCode.HOLD_RELEASE_INVALID_STATUS);
        }

        // Đổi trạng thái sang DISPUTED.
        // Con Robot CronJob sẽ mù màu với đơn này (vì nó chỉ quét status = 'HOLDING')
        hr.setStatus("DISPUTED");
        holdReleaseRepository.save(hr);

        log.info("❄️ Đã ĐÓNG BĂNG số tiền của OrderItem ID {} do có khiếu nại từ người mua.", orderItemId);
    }


    @Transactional
    public void resolveDispute(Long holdReleaseId, boolean isBuyerWin, Long buyerId, Long sellerId, Long orderId) {

        // 1. Tìm bản ghi HoldRelease (Ưu tiên dùng hàm có Lock nếu bạn đã tạo)
        HoldRelease hr = holdReleaseRepository.findById(holdReleaseId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"DISPUTED".equals(hr.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        if (isBuyerWin) {

            // KỊCH BẢN 1: BUYER THẮNG KIỆN
            hr.setStatus("REFUNDED");

            // BƯỚC 1: Gỡ phong tỏa tiền trong ví của Seller (Hủy Hold)
            walletService.cancelHoldForSeller(sellerId, hr.getHoldAmount(), orderId);

            // BƯỚC 2: Hoàn lại tiền mặt vào ví khả dụng cho Buyer
            walletService.addBalance(
                    buyerId,
                    hr.getHoldAmount(),
                    "DISPUTE_REFUND",
                    orderId,
                    "Hoàn tiền do thắng tranh chấp đơn hàng"
            );

            // BƯỚC 3: Hủy hóa đơn thu phí sàn (Vì giao dịch này coi như xịt, sàn không được thu phí)
            if (hr.getFeeLedgerId() != null) {
                feeLedgerRepository.findById(hr.getFeeLedgerId()).ifPresent(feeLedger -> {
                    feeLedger.setStatus("CANCELLED");
                    feeLedgerRepository.save(feeLedger);
                });
            }

            log.info("Tranh chấp ID {}: BUYER thắng. Đã hoàn {} cho Buyer ID {}", holdReleaseId, hr.getHoldAmount(), buyerId);

        } else {
            // KỊCH BẢN 2: SELLER THẮNG KIỆN
            // Trả lại trạng thái HOLDING để con Robot Cronjob tiếp tục đếm ngày và tự nhả tiền cho Seller
            hr.setStatus("HOLDING");
            log.info("Tranh chấp ID {}: SELLER thắng. Tiếp tục giam tiền chờ nhả tự động.", holdReleaseId);
        }

        holdReleaseRepository.save(hr);
    }
}