package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.policy.PreOrderPolicy;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
import com.commercehub.backend.fee.service.FeeCalculationService;
import com.commercehub.backend.order.dto.request.DeliverPreOrderRequest;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        order.setProcessingDeadlineAt(OffsetDateTime.now().plusHours(PreOrderPolicy.PROCESSING_HOURS));
        orderRepository.save(order);

        // Đồng bộ vòng đời pre_order_items bằng một truy vấn thay vì N truy vấn.
        OffsetDateTime acceptedAt = OffsetDateTime.now();
        List<OrderItem> items = orderItemRepository.findByOrder(order);
        List<com.commercehub.backend.order.entity.PreOrderItem> preItems =
                loadPreOrderItems(items).values().stream().toList();
        for (var preItem : preItems) {
            preItem.setStatus("ACCEPTED");
            preItem.setAcceptedAt(acceptedAt);
        }
        preOrderItemRepository.saveAll(preItems);

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

        walletService.systemCancelSellerHold(sellerId, order.getTotalAmount(), order.getId());

        walletService.systemCreditBalance(
                order.getUser().getId(),
                order.getTotalAmount(),
                "ORDER_REFUND",
                order.getId(),
                "ORDER"
        );

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        List<com.commercehub.backend.order.entity.PreOrderItem> preItems =
                loadPreOrderItems(items).values().stream().toList();
        for (var preItem : preItems) {
            preItem.setStatus("REJECTED");
            if (rejectReason != null && !rejectReason.trim().isEmpty()) {
                preItem.setSellerNotes(rejectReason);
            }
        }
        preOrderItemRepository.saveAll(preItems);

        String logNote = "Shop đã từ chối đơn hàng. Lý do: " + (rejectReason != null ? rejectReason : "Không có");
        orderStatusService.logStatusChange(order, oldStatus, "REJECTED", sellerId, logNote);

        log.info(" Shop Owner ID {} đã REJECT đơn {}. Đã hoàn {} vào ví Buyer.", sellerId, orderId, order.getTotalAmount());
    }

    /**
     * Shop giao kết quả đơn PRE_ORDER: nội dung giao khách (account/key/tin nhắn)
     * lưu vào pre_order_items.delivery_content — buyer xem lại vĩnh viễn trong đơn.
     * KHÔNG dùng sellerNotes để giao hàng (chỉ là ghi chú nội bộ, không trả cho buyer).
     */
    @Transactional
    public void completeOrder(Long sellerId, Long orderId, DeliverPreOrderRequest request) {
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
        if (order.getProcessingDeadlineAt() != null
                && OffsetDateTime.now().isAfter(order.getProcessingDeadlineAt())) {
            throw new AppException(ErrorCode.ORDER_APPROVAL_TIMEOUT);
        }

        String oldStatus = order.getStatus();
        OffsetDateTime deliveredAt = OffsetDateTime.now();
        order.setStatus("DELIVERED");
        order.setDeliveredAt(deliveredAt);
        orderRepository.save(order);

        Wallet sellerWallet = walletRepository.findByUserId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        Map<Long, com.commercehub.backend.order.entity.PreOrderItem> preItems =
                loadPreOrderItems(items);
        Map<Long, DeliveryPayload> deliveries = buildDeliveryPayloads(request, items);

        for (OrderItem item : items) {
            var preItem = preItems.get(item.getId());
            DeliveryPayload delivery = deliveries.get(item.getId());
            preItem.setDeliveryContent(delivery.content());
            preItem.setDeliveryContentType(delivery.contentType());
            preItem.setStatus("DELIVERED");
            preItem.setDeliveredAt(deliveredAt);
            preItem.setCompletedAt(deliveredAt);
            if (delivery.sellerNotes() != null && !delivery.sellerNotes().trim().isEmpty()) {
                preItem.setSellerNotes(delivery.sellerNotes());
            }

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
        preOrderItemRepository.saveAll(preItems.values());

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

        walletService.systemCancelSellerHold(order.getShop().getOwner().getId(), order.getTotalAmount(), order.getId());

        walletService.systemCreditBalance(buyerId, order.getTotalAmount(), "ORDER_REFUND", order.getId(), "ORDER");

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        List<com.commercehub.backend.order.entity.PreOrderItem> preItems =
                loadPreOrderItems(items).values().stream().toList();
        for (var preItem : preItems) {
            preItem.setStatus("CANCELLED");
        }
        preOrderItemRepository.saveAll(preItems);

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

        walletService.systemCancelSellerHold(sellerId, order.getTotalAmount(), order.getId());

        walletService.systemCreditBalance(order.getUser().getId(), order.getTotalAmount(), "ORDER_REFUND", order.getId(), "ORDER");

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        List<com.commercehub.backend.order.entity.PreOrderItem> preItems =
                loadPreOrderItems(items).values().stream().toList();
        for (var preItem : preItems) {
            preItem.setStatus("CANCELLED");
            if (cancelReason != null && !cancelReason.trim().isEmpty()) {
                preItem.setSellerNotes(cancelReason);
            }
        }
        preOrderItemRepository.saveAll(preItems);

        orderStatusService.logStatusChange(order, oldStatus, "CANCELLED_BY_SELLER", sellerId, "Shop hủy đơn đang xử lý. Lý do: " + cancelReason);
    }

    private Map<Long, com.commercehub.backend.order.entity.PreOrderItem> loadPreOrderItems(
            List<OrderItem> items) {
        List<Long> itemIds = items.stream().map(OrderItem::getId).toList();
        Map<Long, com.commercehub.backend.order.entity.PreOrderItem> result =
                preOrderItemRepository.findByOrderItemIdIn(itemIds).stream()
                        .collect(Collectors.toMap(
                                preItem -> preItem.getOrderItem().getId(),
                                Function.identity()
                        ));
        if (result.size() != itemIds.size()) {
            throw new AppException(ErrorCode.RECORD_NOT_FOUND);
        }
        return result;
    }

    private Map<Long, DeliveryPayload> buildDeliveryPayloads(
            DeliverPreOrderRequest request,
            List<OrderItem> orderItems) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            if (orderItems.size() != 1
                    || request.getDeliveryContentType() == null
                    || request.getDeliveryContent() == null
                    || request.getDeliveryContent().isBlank()) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            return Map.of(orderItems.getFirst().getId(), new DeliveryPayload(
                    request.getDeliveryContentType(),
                    request.getDeliveryContent(),
                    request.getSellerNotes()
            ));
        }

        Map<Long, DeliverPreOrderRequest.DeliveryItem> requested;
        try {
            requested = request.getItems().stream().collect(Collectors.toMap(
                    DeliverPreOrderRequest.DeliveryItem::getOrderItemId,
                    Function.identity()
            ));
        } catch (IllegalStateException exception) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        List<Long> actualIds = orderItems.stream().map(OrderItem::getId).toList();
        if (requested.size() != actualIds.size() || !requested.keySet().containsAll(actualIds)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return requested.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new DeliveryPayload(
                        entry.getValue().getDeliveryContentType(),
                        entry.getValue().getDeliveryContent(),
                        entry.getValue().getSellerNotes()
                )
        ));
    }

    private record DeliveryPayload(
            com.commercehub.backend.order.entity.DeliveryContentType contentType,
            String content,
            String sellerNotes) {
    }
}
