package com.commercehub.backend.order.service;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderStatusLog;
import com.commercehub.backend.order.repository.OrderStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private final OrderStatusLogRepository statusLogRepository;

    @Transactional
    public void logStatusChange(Order order, String fromStatus, String toStatus, Long changedById, String note) {
        OrderStatusLog log = OrderStatusLog.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedById) // Có thể null nếu do hệ thống
                .note(note)
                .build();
        statusLogRepository.save(log);
    }
}