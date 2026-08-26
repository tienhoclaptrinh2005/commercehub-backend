package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.service.PlatformFeeLedgerService;
import com.commercehub.backend.product.repository.ProductRepository;
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
    private final PlatformFeeLedgerService platformFeeLedgerService;
    private final ProductRepository productRepository;

    //   private final OutboxEventService outboxEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingle(Long holdReleaseId) {

        HoldRelease hr = holdReleaseRepository.findByIdWithLock(holdReleaseId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"HOLDING".equals(hr.getStatus())) {
            log.info("BỎ QUA: HoldRelease ID {} đã bị đổi trạng thái thành {} (có thể do khiếu nại).", hr.getId(), hr.getStatus());
            return;
        }

        walletService.systemReleaseHold(
                hr.getWallet().getUser().getId(),
                hr.getHoldAmount(),
                hr.getSellerNetAmount(),
                hr.getFeeAmount(),
                hr.getId()
        );

        hr.setStatus("RELEASED");
        hr.setReleasedAt(OffsetDateTime.now());
        holdReleaseRepository.save(hr);

        if (hr.getFeeLedgerId() != null) {
            platformFeeLedgerService.markAsCollected(hr.getFeeLedgerId());
        }

        if (hr.getOrderItemId() != null
                && productRepository.incrementSoldCountByOrderItemId(hr.getOrderItemId()) != 1) {
            log.warn("Không cập nhật được sold_count cho orderItem {}", hr.getOrderItemId());
        }

        // 5. Đẩy event sang Outbox để gửi thông báo/email
        String payload = String.format(Locale.US, "{\"fee_ledger_id\": %d, \"fee_amount\": \"%s\", \"seller_net\": \"%s\"}",
                hr.getFeeLedgerId(), hr.getFeeAmount().toPlainString(), hr.getSellerNetAmount().toPlainString());
        //       outboxEventService.publish("PLATFORM_FEE", hr.getId(), "FEE_COLLECTED", payload);
    }
}
