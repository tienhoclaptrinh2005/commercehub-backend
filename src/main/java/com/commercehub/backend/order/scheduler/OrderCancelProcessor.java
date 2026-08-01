package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.repository.OrderRepository;
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
    private final WalletService walletService;
    private final OrderStatusService orderStatusService;

    // BẮT BUỘC DÙNG REQUIRES_NEW ĐỂ TÁCH BIỆT GIAO DỊCH, TRÁNH CHẾT CHÙM
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelSingleOrder(Long orderId, String reason) {

        // 1. Re-fetch Order để khởi tạo lại Session an toàn, tránh lỗi Lazy
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        String oldStatus = order.getStatus();

        // 2. Cập nhật trạng thái
        order.setStatus("CANCELLED_BY_SYSTEM");
        order.setPaymentStatus("REFUNDED");
        orderRepository.save(order);

        // 3. Xử lý hoàn tiền
        // Do đã re-fetch an toàn ở bước 1, gọi order.getShop().getOwner() giờ sẽ cực kỳ mượt mà
        walletService.cancelHoldForSeller(order.getShop().getOwner().getId(), order.getTotalAmount(), order.getId());
        walletService.addBalance(order.getUser().getId(), order.getTotalAmount(), "ORDER_REFUND", order.getId(), reason);

        // 4. Ghi log trạng thái
        orderStatusService.logStatusChange(order, oldStatus, "CANCELLED_BY_SYSTEM", null, reason);

        log.info("CronJob đã hủy thành công đơn hàng ID: {}", orderId);
    }
}