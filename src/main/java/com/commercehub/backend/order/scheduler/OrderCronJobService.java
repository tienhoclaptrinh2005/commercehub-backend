package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCronJobService {

    private final OrderRepository orderRepository;
    private final OrderCancelProcessor orderCancelProcessor;

    @Scheduled(cron = "0 0/30 * * * *")
    public void autoCancelExpiredProcessingOrders() {
        List<Order> expiredOrders = orderRepository.findByStatusAndProcessingDeadlineAtBefore("PROCESSING", OffsetDateTime.now());

        for (Order order : expiredOrders) {
            try {
                // ĐÃ SỬA: Chỉ truyền order.getId() vào để bên processor tự re-fetch an toàn trong Transaction riêng
                orderCancelProcessor.cancelSingleOrder(
                        order.getId(),
                        "Hệ thống tự động hủy đơn và hoàn tiền do Shop không hoàn thành trong 24h (Mã: " + order.getOrderCode() + ")"
                );
            } catch (Exception e) {
                log.error("Lỗi khi auto-cancel PROCESSING đơn hàng ID {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0/30 * * * *")
    public void autoCancelExpiredWaitingApproval() {
        List<Order> expiredOrders = orderRepository.findByStatusAndApprovalDeadlineAtBefore("WAITING_APPROVAL", OffsetDateTime.now());

        for (Order order : expiredOrders) {
            try {
                // ĐÃ SỬA: Chỉ truyền order.getId()
                orderCancelProcessor.cancelSingleOrder(
                        order.getId(),
                        "Hệ thống tự động hủy đơn do Shop treo quá 48h không duyệt (Mã: " + order.getOrderCode() + ")"
                );
            } catch (Exception e) {
                log.error("Lỗi khi auto-cancel WAITING_APPROVAL đơn hàng ID {}: {}", order.getId(), e.getMessage());
            }
        }
    }
}