package com.commercehub.backend.wallet.scheduler;

import com.commercehub.backend.wallet.service.HoldReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Scheduler chạy định kỳ để giải phóng tiền Hold cho Seller.
 *
 * Luồng:
 *   1. Buyer checkout → Tiền buyer bị trừ, tiền được Hold vào ví seller.
 *   2. HoldRelease record được tạo với scheduledReleaseAt = now + 7 ngày.
 *   3. Scheduler này chạy mỗi 5 phút, tìm các HoldRelease đã đến hạn (scheduledReleaseAt <= now).
 *   4. Với mỗi record: Trừ holdBalance, cộng availableBalance (net), thu phí sàn vào ví platform.
 *   5. Nếu Buyer confirm sớm → earlyRelease được gọi từ OrderService, không cần đợi scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldReleaseScheduler {

    private final HoldReleaseService holdReleaseService;

    /**
     * Chạy mỗi 5 phút để tìm và xử lý các khoản Hold đã đến hạn giải phóng.
     * Cron: giây 0, mỗi 5 phút, mọi giờ, mọi ngày.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void releaseHeldFunds() {
        log.info("⏰ [HoldReleaseScheduler] Bắt đầu quét các khoản hold đến hạn...");
        try {
            holdReleaseService.processDueReleases(OffsetDateTime.now());
            log.info("✅ [HoldReleaseScheduler] Hoàn thành quét hold release.");
        } catch (Exception e) {
            log.error("❌ [HoldReleaseScheduler] Lỗi khi quét hold release: {}", e.getMessage(), e);
        }
    }
}
