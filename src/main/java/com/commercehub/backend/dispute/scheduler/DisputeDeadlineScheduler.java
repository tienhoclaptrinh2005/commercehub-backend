package com.commercehub.backend.dispute.scheduler;

import com.commercehub.backend.dispute.entity.OrderDispute;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.dispute.service.DisputeResolutionService;
import com.commercehub.backend.dispute.service.DisputeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeDeadlineScheduler {

    private static final int MAX_BATCHES_PER_RUN = 20;

    private final OrderDisputeRepository disputeRepository;
    private final DisputeResolutionService resolutionService;
    private final DisputeService disputeService;

    @Value("${commercehub.jobs.batch-size:200}")
    private int batchSize;

    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "dispute_processDeadlines", lockAtMostFor = "4m", lockAtLeastFor = "10s")
    public void processDeadlines() {
        OffsetDateTime now = OffsetDateTime.now();
        processStatus(OrderDispute.STATUS_OPEN, now);
        processStatus(OrderDispute.STATUS_WAITING_BUYER_CONFIRMATION, now);
    }

    private void processStatus(String status, OffsetDateTime now) {
        int safeBatchSize = Math.min(Math.max(batchSize, 1), 500);
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Long> ids = disputeRepository.findExpiredIds(
                    status,
                    now,
                    PageRequest.of(0, safeBatchSize)
            );
            if (ids.isEmpty()) {
                return;
            }

            for (Long id : ids) {
                try {
                    if (OrderDispute.STATUS_OPEN.equals(status)) {
                        resolutionService.resolveExpiredOpenForBuyer(id, now);
                    } else {
                        disputeService.closeExpiredBuyerConfirmation(id, now);
                    }
                } catch (Exception exception) {
                    log.error("Không xử lý được dispute quá hạn ID {}: {}", id, exception.getMessage(), exception);
                }
            }
        }
        log.warn("Dispute deadline job đạt giới hạn {} batch; phần còn lại xử lý ở lượt sau", MAX_BATCHES_PER_RUN);
    }
}
