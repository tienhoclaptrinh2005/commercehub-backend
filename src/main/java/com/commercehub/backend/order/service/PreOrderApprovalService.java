package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
import com.commercehub.backend.fee.service.FeeCalculationService;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.fee.entity.PlatformFeeLedger;
import com.commercehub.backend.fee.dto.FeeResult;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreOrderApprovalService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PreOrderItemRepository preOrderItemRepository;
    private final OrderStatusService orderStatusService;
    private final WalletService walletService;
    private final FeeCalculationService feeCalculationService;
    private final PlatformFeeLedgerRepository feeLedgerRepository;
    private final HoldReleaseRepository holdReleaseRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public void acceptOrder(Long sellerId, Long orderId) {
        // PESSIMISTIC LOCK: chống race với cron auto-cancel — không bao giờ
        // xảy ra "buyer đã được hoàn tiền nhưng đơn vẫn chuyển PROCESSING".
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (!"PRE_ORDER".equals(order.getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
        }
        if (!"WAITING_APPROVAL".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_NOT_WAITING_APPROVAL);
        }

        if (order.getApprovalDeadlineAt() != null && OffsetDateTime.now().isAfter(order.getApprovalDeadlineAt())) {
            throw new AppException(ErrorCode.ORDER_APPROVAL_TIMEOUT);
        }

        String oldStatus = order.getStatus();
        order.setStatus("PROCESSING");
        order.setApprovedAt(OffsetDateTime.now());
        order.setProcessingDeadlineAt(OffsetDateTime.now().plusHours(24));
        orderRepository.save(order);

        orderStatusService.logStatusChange(
                order,
                oldStatus,
                "PROCESSING",
                sellerId,
                "Shop đã tiếp nhận đơn đặt hàng và đang tiến hành xử lý."
        );

        log.info(" Shop Owner ID {} đã ACCEPT đơn hàng đặt trước ID {}", sellerId, orderId);
    }

    @Transactional
    public void rejectOrder(Long sellerId, Long orderId, String rejectReason) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (!"PRE_ORDER".equals(order.getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
        }
        if (!"WAITING_APPROVAL".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_NOT_WAITING_APPROVAL);
        }

        String oldStatus = order.getStatus();
        order.setStatus("REJECTED");
        order.setPaymentStatus("REFUNDED");
        order.setRejectedAt(OffsetDateTime.now());
        order.setRejectionReason(rejectReason);
        orderRepository.save(order);

        walletService.cancelHoldForSeller(sellerId, order.getTotalAmount(), order.getId());

        walletService.addBalance(
                order.getUser().getId(),
                order.getTotalAmount(),
                "ORDER_REFUND",
                order.getId(),
                "ORDER"
        );

        if (rejectReason != null && !rejectReason.trim().isEmpty()) {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            for (OrderItem item : items) {
                preOrderItemRepository.findByOrderItemId(item.getId()).ifPresent(preItem -> {
                    preItem.setSellerNotes(rejectReason);
                    preOrderItemRepository.save(preItem);
                });
            }
        }

        String logNote = "Shop đã từ chối đơn hàng. Lý do: " + (rejectReason != null ? rejectReason : "Không có");
        orderStatusService.logStatusChange(order, oldStatus, "REJECTED", sellerId, logNote);

        log.info(" Shop Owner ID {} đã REJECT đơn {}. Đã hoàn {} vào ví Buyer.", sellerId, orderId, order.getTotalAmount());
    }

    @Transactional
    public void completeOrder(Long sellerId, Long orderId, String sellerNotes) {
        // PESSIMISTIC LOCK: chống double-click Complete tạo 2 bộ HoldRelease,
        // và chống race với cron auto-cancel đơn quá hạn.
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (!"PRE_ORDER".equals(order.getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
        }

        if (!"PROCESSING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_NOT_PROCESSING);
        }

        String oldStatus = order.getStatus();
        order.setStatus("DELIVERED");
        order.setDeliveredAt(OffsetDateTime.now());
        orderRepository.save(order);

        Wallet sellerWallet = walletRepository.findByUserId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        List<OrderItem> items = orderItemRepository.findByOrder(order);

        for (OrderItem item : items) {
            preOrderItemRepository.findByOrderItemId(item.getId()).ifPresent(preItem -> {
                if (sellerNotes != null && !sellerNotes.trim().isEmpty()) {
                    preItem.setSellerNotes(sellerNotes);
                    preOrderItemRepository.save(preItem);
                }
            });

            // Dùng SNAPSHOT phí đã chốt lúc buyer thanh toán (checkout).
            // Fallback tính theo config hiện tại cho các đơn cũ tạo trước khi có snapshot.
            FeeResult feeResult = resolveFeeForItem(item);

            HoldRelease holdRelease = HoldRelease.builder()
                    .wallet(sellerWallet)
                    .orderId(order.getId())
                    .orderItemId(item.getId())
                    .holdAmount(item.getLineTotal())
                    .feeAmount(feeResult.getFeeAmount())
                    .sellerNetAmount(feeResult.getSellerNetAmount())
                    .status("HOLDING")
                    .scheduledReleaseAt(OffsetDateTime.now().plusDays(7))
                    .build();
            holdRelease = holdReleaseRepository.save(holdRelease);

            PlatformFeeLedger feeLedger = PlatformFeeLedger.builder()
                    .orderId(order.getId())
                    .orderItemId(item.getId())
                    .shopId(order.getShop().getId())
                    .sellerWalletId(sellerWallet.getId())
                    .feeConfigId(feeResult.getFeeConfigId())
                    .feeRateSnapshot(feeResult.getFeeRateSnapshot())
                    .saleAmount(item.getLineTotal())
                    .feeAmount(feeResult.getFeeAmount())
                    .sellerNetAmount(feeResult.getSellerNetAmount())
                    .status("PENDING")
                    .feeIncurredAt(OffsetDateTime.now())
                    .holdReleaseId(holdRelease.getId())
                    .build();
            feeLedger = feeLedgerRepository.save(feeLedger);

            // Cập nhật lại FeeLedgerId vào HoldRelease
            holdRelease.setFeeLedgerId(feeLedger.getId());
            holdReleaseRepository.save(holdRelease);
        }

        orderStatusService.logStatusChange(order, oldStatus, "DELIVERED", sellerId, "Shop đã hoàn tất giao hàng/dịch vụ.");
        log.info(" Shop Owner {} đã COMPLETE đơn {}. Đã tính phí và tạo lịch nhả tiền cho từng item.", sellerId, orderId);
    }

    /**
     * Lấy FeeResult cho 1 OrderItem: ưu tiên snapshot đã chốt lúc checkout;
     * đơn cũ chưa có snapshot thì tính theo config hiện tại.
     */
    private FeeResult resolveFeeForItem(OrderItem item) {
        if (item.getFeeAmount() != null && item.getSellerNetAmount() != null
                && item.getFeeConfigId() != null) {
            return FeeResult.builder()
                    .feeConfigId(item.getFeeConfigId())
                    .feeRateSnapshot(item.getFeeRateSnapshot())
                    .feeAmount(item.getFeeAmount())
                    .sellerNetAmount(item.getSellerNetAmount())
                    .build();
        }
        return feeCalculationService.calculateFee(item.getLineTotal());
    }

    @Transactional
    public void cancelOrderByBuyer(Long buyerId, Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getUser().getId().equals(buyerId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (!"WAITING_APPROVAL".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        String oldStatus = order.getStatus();
        order.setStatus("CANCELLED");
        order.setPaymentStatus("REFUNDED");
        orderRepository.save(order);

        walletService.cancelHoldForSeller(order.getShop().getOwner().getId(), order.getTotalAmount(), order.getId());

        walletService.addBalance(buyerId, order.getTotalAmount(), "ORDER_REFUND", order.getId(), "ORDER");

        orderStatusService.logStatusChange(order, oldStatus, "CANCELLED", buyerId, "Người mua đã chủ động hủy đơn hàng trước khi Shop tiếp nhận.");
    }

    @Transactional
    public void cancelProcessingOrder(Long sellerId, Long orderId, String cancelReason) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (!"PROCESSING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_NOT_PROCESSING);
        }

        String oldStatus = order.getStatus();
        order.setStatus("CANCELLED_BY_SELLER");
        order.setPaymentStatus("REFUNDED");
        orderRepository.save(order);

        walletService.cancelHoldForSeller(sellerId, order.getTotalAmount(), order.getId());

        walletService.addBalance(order.getUser().getId(), order.getTotalAmount(), "ORDER_REFUND", order.getId(), "ORDER");

        if (cancelReason != null && !cancelReason.trim().isEmpty()) {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            for (OrderItem item : items) {
                preOrderItemRepository.findByOrderItemId(item.getId()).ifPresent(preItem -> {
                    preItem.setSellerNotes(cancelReason);
                    preOrderItemRepository.save(preItem);
                });
            }
        }

        orderStatusService.logStatusChange(order, oldStatus, "CANCELLED_BY_SELLER", sellerId, "Shop hủy đơn đang xử lý. Lý do: " + cancelReason);
    }
}