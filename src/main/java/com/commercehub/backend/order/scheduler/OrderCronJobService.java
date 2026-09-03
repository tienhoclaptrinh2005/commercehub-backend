package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCronJobService {

    private static final int MAX_BATCHES_PER_RUN = 20;

    private final OrderRepository orderRepository;
    private final OrderCancelProcessor orderCancelProcessor;

    @Value("${commercehub.jobs.batch-size:200}")
    private int batchSize;

    @Scheduled(cron = "0 0/30 * * * *")
    @SchedulerLock(name = "order_autoCancelExpiredProcessing", lockAtMostFor = "25m", lockAtLeastFor = "30s")
    public void autoCancelExpiredProcessingOrders() {
        processExpired("PROCESSING", OffsetDateTime.now());
    }

    @Scheduled(cron = "0 0/30 * * * *")
    @SchedulerLock(name = "order_autoCancelExpiredWaitingApproval", lockAtMostFor = "25m", lockAtLeastFor = "30s")
    public void autoCancelExpiredWaitingApproval() {
        processExpired("WAITING_APPROVAL", OffsetDateTime.now());
    }

    private void processExpired(String status, OffsetDateTime now) {
        int safeBatchSize = Math.min(Math.max(batchSize, 1), 500);
        Set<Long> failedOrderIds = new HashSet<>();
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Long> ids = "PROCESSING".equals(status)
                    ? orderRepository.findExpiredProcessingIds(status, now, PageRequest.of(0, safeBatchSize))
                    : orderRepository.findExpiredApprovalIds(status, now, PageRequest.of(0, safeBatchSize));
            if (ids.isEmpty()) {
                return;
            }

            // Bản ghi lỗi vẫn còn quá hạn sau rollback và sẽ xuất hiện lại ở trang đầu.
            // Không thử lại cùng bản ghi 20 lần trong một lượt cron để tránh spam log/DB.
            List<Long> pendingIds = ids.stream()
                    .filter(id -> !failedOrderIds.contains(id))
                    .toList();
            if (pendingIds.isEmpty()) {
                log.warn("Order cron {} dừng lượt quét vì {} đơn lỗi đã được thử trong lượt này; sẽ thử lại ở lịch kế tiếp",
                        status, failedOrderIds.size());
                return;
            }

            for (Long orderId : pendingIds) {
                try {
                    orderCancelProcessor.cancelSingleOrder(
                            orderId,
                            "Hệ thống tự động hủy và hoàn tiền do đơn quá hạn ở trạng thái " + status
                    );
                } catch (Exception e) {
                    failedOrderIds.add(orderId);
                    log.error("Lỗi auto-cancel đơn ID {}: {}", orderId, e.getMessage(), e);
                }
            }
        }
        log.warn("Order cron {} đạt giới hạn {} batch; phần còn lại xử lý ở lượt sau", status, MAX_BATCHES_PER_RUN);
    }
}
