package com.commercehub.backend.wallet.scheduler;

import com.commercehub.backend.wallet.service.DepositService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositExpiryScheduler {

    private final DepositService depositService;

    @Scheduled(fixedDelayString = "${sepay.deposit-expiry-scan-ms:60000}")
    @SchedulerLock(name = "deposit_expirePending", lockAtMostFor = "50s", lockAtLeastFor = "5s")
    public void expirePendingDeposits() {
        int expired = depositService.expirePendingDeposits();
        if (expired > 0) {
            log.info("Đã hết hạn {} yêu cầu nạp tiền SePay", expired);
        }
    }
}
