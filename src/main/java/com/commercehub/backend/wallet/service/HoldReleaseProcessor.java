package com.commercehub.backend.wallet.service;

//import com.commercehub.backend.outbox.service.OutboxEventService;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class HoldReleaseProcessor {

    private final WalletService walletService;
    private final HoldReleaseRepository holdReleaseRepository;
 //   private final OutboxEventService outboxEventService;

    @Transactional
    public void processSingle(HoldRelease hr) {

        walletService.processHoldRelease(
                hr.getWallet().getUser().getId(),
                hr.getHoldAmount(),
                hr.getSellerNetAmount(),
                hr.getFeeAmount(),
                hr.getId()
        );


        hr.setStatus("RELEASED");
        hr.setReleasedAt(OffsetDateTime.now());
        holdReleaseRepository.save(hr);

        // 3. Đẩy event sang Outbox để gửi thông báo/email
        String payload = String.format("{\"fee_ledger_id\": %d, \"fee_amount\": %f, \"seller_net\": %f}",
                hr.getFeeLedgerId(), hr.getFeeAmount(), hr.getSellerNetAmount());
 //       outboxEventService.publish("PLATFORM_FEE", hr.getId(), "FEE_COLLECTED", payload);
    }
}