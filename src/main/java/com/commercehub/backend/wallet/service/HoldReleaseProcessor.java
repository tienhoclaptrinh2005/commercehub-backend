package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
//import com.commercehub.backend.outbox.service.OutboxEventService;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldReleaseProcessor {

    private final WalletService walletService;
    private final HoldReleaseRepository holdReleaseRepository;
    private final PlatformFeeLedgerRepository feeLedgerRepository;

    //   private final OutboxEventService outboxEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingle(HoldRelease hrParam) {

        // 1. Dùng ID lấy lại dữ liệu mới nhất từ DB VÀ KHÓA DÒNG NÀY LẠI
        HoldRelease hr = holdReleaseRepository.findByIdWithLock(hrParam.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        // 2. Kiểm tra chéo (Double-check) trạng thái thực tế
        if (!"HOLDING".equals(hr.getStatus())) {
            log.info("BỎ QUA: HoldRelease ID {} đã bị đổi trạng thái thành {} (có thể do khiếu nại).", hr.getId(), hr.getStatus());
            return;
        }

        // 3. Tiến hành trừ tiền giam và cộng vào ví khả dụng
        walletService.processHoldRelease(
                hr.getWallet().getUser().getId(),
                hr.getHoldAmount(),
                hr.getSellerNetAmount(),
                hr.getFeeAmount(),
                hr.getId()
        );

        // 4. Cập nhật trạng thái đã nhả tiền
        hr.setStatus("RELEASED");
        hr.setReleasedAt(OffsetDateTime.now());
        holdReleaseRepository.save(hr);

        // 👉 FIX BUG #38: Ghi nhận đã thu phí sàn thành công vào báo cáo tài chính
        if (hr.getFeeLedgerId() != null) {
            feeLedgerRepository.findById(hr.getFeeLedgerId()).ifPresent(feeLedger -> {
                feeLedger.setStatus("COLLECTED");
                feeLedger.setCollectedAt(OffsetDateTime.now());
                feeLedgerRepository.save(feeLedger);
            });
        }

        // 5. Đẩy event sang Outbox để gửi thông báo/email
        String payload = String.format(Locale.US,"{\"fee_ledger_id\": %d, \"fee_amount\": %f, \"seller_net\": %f}",
                hr.getFeeLedgerId(), hr.getFeeAmount(), hr.getSellerNetAmount());
        //       outboxEventService.publish("PLATFORM_FEE", hr.getId(), "FEE_COLLECTED", payload);
    }
}