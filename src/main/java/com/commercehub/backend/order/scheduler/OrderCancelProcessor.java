package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderCancellationCode;
import com.commercehub.backend.order.entity.OrderCancelledBy;
import com.commercehub.backend.order.entity.OrderPaymentStatus;
import com.commercehub.backend.order.entity.OrderStatus;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.service.OrderStatusService;
import com.commercehub.backend.wallet.service.WalletService;
import com.commercehub.backend.voucher.service.VoucherService;
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
    private final WalletService walletService;
    private final OrderStatusService orderStatusService;
    private final VoucherService voucherService;

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
        OrderStatus status = order.getStatus();
        boolean cancellableStatus = status == OrderStatus.WAITING_SELLER_ACCEPTANCE
                || status == OrderStatus.PROCESSING;
        if (!cancellableStatus || order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            log.info("Bỏ qua auto-cancel đơn ID {}: status={}, paymentStatus={} (không đủ điều kiện hủy).",
                    orderId, status, order.getPaymentStatus());
            return;
        }

        // 3. Re-check deadline sau khi có lock — seller có thể vừa accept làm deadline thay đổi
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        boolean waitingExpired = status == OrderStatus.WAITING_SELLER_ACCEPTANCE
                && order.getApprovalDeadlineAt() != null && order.getApprovalDeadlineAt().isBefore(now);
        boolean processingExpired = status == OrderStatus.PROCESSING
                && order.getProcessingDeadlineAt() != null && order.getProcessingDeadlineAt().isBefore(now);
        if (!waitingExpired && !processingExpired) {
            log.info("Bỏ qua auto-cancel đơn ID {}: deadline đã được gia hạn (seller vừa thao tác).", orderId);
            return;
        }

        OrderStatus oldStatus = order.getStatus();

        // 4. Cập nhật trạng thái
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
        order.setCancelledBy(OrderCancelledBy.SYSTEM);
        order.setCancellationCode(waitingExpired
                ? OrderCancellationCode.SELLER_ACCEPTANCE_TIMEOUT
                : OrderCancellationCode.SELLER_PROCESSING_TIMEOUT);
        order.setCancellationReason(reason);
        order.setCancelledAt(now);
        orderRepository.save(order);

        // 5. Hoàn tiền: gỡ hold của seller, trả tiền về ví buyer (cùng transaction)
        walletService.systemCancelSellerHold(order.getShop().getOwner().getId(), order.getTotalAmount(), order.getId());
        // referenceType là loại bản ghi được referenceId trỏ tới, không phải nội dung mô tả.
        // Truyền `reason` vào đây làm PostgreSQL từ chối vì cột VARCHAR(30).
        walletService.systemCreditBalance(order.getUser().getId(), order.getTotalAmount(), "ORDER_REFUND", order.getId(), "ORDER");
        voucherService.releaseUsageForCancelledOrder(order.getId());

        // 6. Ghi log trạng thái
        orderStatusService.logStatusChange(order, oldStatus, OrderStatus.CANCELLED, null, reason);

        log.info("CronJob đã hủy thành công đơn hàng ID: {}", orderId);
    }
}
