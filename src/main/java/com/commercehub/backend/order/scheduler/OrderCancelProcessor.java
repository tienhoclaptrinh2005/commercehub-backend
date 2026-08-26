package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.order.service.OrderStatusService;
import com.commercehub.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelProcessor {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PreOrderItemRepository preOrderItemRepository;
    private final WalletService walletService;
    private final OrderStatusService orderStatusService;

    // BẮT BUỘC DÙNG REQUIRES_NEW ĐỂ TÁCH BIỆT GIAO DỊCH, TRÁNH CHẾT CHÙM
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelSingleOrder(Long orderId, String reason) {

        // 1. PESSIMISTIC LOCK: chống race với seller accept/complete/cancel.
        // Nếu seller đang thao tác, cron sẽ đợi lock rồi đọc trạng thái MỚI NHẤT.
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        // 2. GUARD trạng thái: chỉ hủy đơn còn đang chờ/đang xử lý và CHƯA hoàn tiền.
        // Đơn vừa được seller accept (PROCESSING với deadline mới) hoặc đã DELIVERED
        // / CANCELLED / REFUNDED thì tuyệt đối không đụng vào tiền.
        String status = order.getStatus();
        boolean cancellableStatus = "WAITING_APPROVAL".equals(status) || "PROCESSING".equals(status);
        if (!cancellableStatus || !"PAID".equals(order.getPaymentStatus())) {
            log.info("Bỏ qua auto-cancel đơn ID {}: status={}, paymentStatus={} (không đủ điều kiện hủy).",
                    orderId, status, order.getPaymentStatus());
            return;
        }

        // 3. Re-check deadline sau khi có lock — seller có thể vừa accept làm deadline thay đổi
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        boolean waitingExpired = "WAITING_APPROVAL".equals(status)
                && order.getApprovalDeadlineAt() != null && order.getApprovalDeadlineAt().isBefore(now);
        boolean processingExpired = "PROCESSING".equals(status)
                && order.getProcessingDeadlineAt() != null && order.getProcessingDeadlineAt().isBefore(now);
        if (!waitingExpired && !processingExpired) {
            log.info("Bỏ qua auto-cancel đơn ID {}: deadline đã được gia hạn (seller vừa thao tác).", orderId);
            return;
        }

        String oldStatus = order.getStatus();

        // 4. Cập nhật trạng thái
        order.setStatus("CANCELLED_BY_SYSTEM");
        order.setPaymentStatus("REFUNDED");
        orderRepository.save(order);

        // 5. Hoàn tiền: gỡ hold của seller, trả tiền về ví buyer (cùng transaction)
        walletService.systemCancelSellerHold(order.getShop().getOwner().getId(), order.getTotalAmount(), order.getId());
        walletService.systemCreditBalance(order.getUser().getId(), order.getTotalAmount(), "ORDER_REFUND", order.getId(), reason);

        // Đồng bộ vòng đời pre_order_items khi hệ thống tự hủy
        orderItemRepository.findByOrder(order).forEach(item ->
                preOrderItemRepository.findByOrderItemId(item.getId()).ifPresent(preItem -> {
                    preItem.setStatus("CANCELLED");
                    preOrderItemRepository.save(preItem);
                }));

        // 6. Ghi log trạng thái
        orderStatusService.logStatusChange(order, oldStatus, "CANCELLED_BY_SYSTEM", null, reason);

        log.info("CronJob đã hủy thành công đơn hàng ID: {}", orderId);
    }
}
