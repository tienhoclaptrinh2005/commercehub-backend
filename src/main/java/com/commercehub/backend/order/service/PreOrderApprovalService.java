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

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // 2. Validate quyền sở hữu (Bảo mật: Chỉ chủ shop mới được duyệt đơn của shop mình)
        if (!order.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        // 3. Validate loại giao hàng và trạng thái
        if (!"PRE_ORDER".equals(order.getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE); // Hoặc tạo mã mới
        }
        if (!"WAITING_APPROVAL".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_NOT_WAITING_APPROVAL); // Trạng thái không hợp lệ
        }

        // 4. Validate thời gian (Nếu đã quá hạn duyệt thì không cho duyệt nữa, chờ CronJob hủy)
        if (order.getApprovalDeadlineAt() != null && OffsetDateTime.now().isAfter(order.getApprovalDeadlineAt())) {
            throw new AppException(ErrorCode.ORDER_APPROVAL_TIMEOUT);
        }

        // 5. Cập nhật trạng thái sang PROCESSING (Đang xử lý/chuẩn bị hàng)
        String oldStatus = order.getStatus();
        order.setStatus("PROCESSING");
        // ĐÃ FIX BUG #21: Ghi nhận thời gian duyệt đơn
        order.setApprovedAt(OffsetDateTime.now());
        order.setProcessingDeadlineAt(OffsetDateTime.now().plusHours(24));
        orderRepository.save(order);

        // 6. Ghi log lịch sử thay đổi trạng thái
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

        // 1. Tìm đơn hàng & Validate quyền
        Order order = orderRepository.findById(orderId)
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

        // 2. Cập nhật trạng thái đơn hàng
        String oldStatus = order.getStatus();
        order.setStatus("REJECTED");
        order.setPaymentStatus("REFUNDED");

        // ĐÃ FIX BUG #22: Bổ sung mốc thời gian và lý do từ chối
        order.setRejectedAt(OffsetDateTime.now());
        order.setRejectionReason(rejectReason);

        orderRepository.save(order);

        // 3. Hoàn tiền (Refund) thông qua WalletService
        // ĐÃ FIX Bug #24: Truyền order.getId() thay vì null
        walletService.cancelHoldForSeller(sellerId, order.getTotalAmount(), order.getId());

        walletService.addBalance(
                order.getUser().getId(),
                order.getTotalAmount(),
                "ORDER_REFUND",
                order.getId(),
                "Hoàn tiền đơn hàng bị Shop từ chối (Mã: " + order.getOrderCode() + ")"
        );

        // 4. Lưu lý do từ chối vào PreOrderItem
        if (rejectReason != null && !rejectReason.trim().isEmpty()) {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            for (OrderItem item : items) {
                preOrderItemRepository.findByOrderItemId(item.getId()).ifPresent(preItem -> {
                    preItem.setSellerNotes(rejectReason);
                    preOrderItemRepository.save(preItem);
                });
            }
        }

        // 5. Ghi log trạng thái
        String logNote = "Shop đã từ chối đơn hàng. Lý do: " + (rejectReason != null ? rejectReason : "Không có");
        orderStatusService.logStatusChange(order, oldStatus, "REJECTED", sellerId, logNote);

        log.info(" Shop Owner ID {} đã REJECT đơn {}. Đã hoàn {} vào ví Buyer.", sellerId, orderId, order.getTotalAmount());
    }



    @Transactional
    public void completeOrder(Long sellerId, Long orderId, String sellerNotes) {

        // 1. Tìm đơn & Validate quyền
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (!"PRE_ORDER".equals(order.getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
        }

        // ⚠️ Chú ý: Chỉ đơn đang PROCESSING (đã accept) mới được phép complete
        if (!"PROCESSING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_NOT_PROCESSING);
        }

        // 2. Cập nhật trạng thái Order thành DELIVERED (Đã giao hàng)
        String oldStatus = order.getStatus();
        order.setStatus("DELIVERED");

        // ĐÃ FIX BUG #23: Bổ sung mốc thời gian hoàn thành/giao hàng
        order.setDeliveredAt(OffsetDateTime.now());

        orderRepository.save(order);

        // 3. Tính phí sàn và Tạo lịch nhả tiền (HoldRelease) cho từng món hàng
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

            // Gọi logic tính phí
            FeeResult feeResult = feeCalculationService.calculateFee(item.getLineTotal());

            // Tạo HoldRelease (Lên lịch nhả tiền)
            HoldRelease holdRelease = HoldRelease.builder()
                    .wallet(sellerWallet)
                    .orderItemId(item.getId())
                    .holdAmount(item.getLineTotal())
                    .feeAmount(feeResult.getFeeAmount())
                    .sellerNetAmount(feeResult.getSellerNetAmount())
                    .status("HOLDING")
                    .scheduledReleaseAt(OffsetDateTime.now().plusDays(7))
                    .build();
            holdRelease = holdReleaseRepository.save(holdRelease);

            // Ghi sổ cái phí sàn (PlatformFeeLedger)
            PlatformFeeLedger feeLedger = PlatformFeeLedger.builder()
                    .orderItemId(item.getId())
                    .orderId(order.getId())
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

            holdRelease.setFeeLedgerId(feeLedger.getId());
            holdReleaseRepository.save(holdRelease);
        }

        // 4. Ghi log
        orderStatusService.logStatusChange(order, oldStatus, "DELIVERED", sellerId, "Shop đã hoàn tất giao hàng/dịch vụ.");
        log.info(" Shop Owner {} đã COMPLETE đơn {}. Đã tính phí và tạo lịch nhả tiền.", sellerId, orderId);
    }


    @Transactional
    public void cancelOrderByBuyer(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
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
        walletService.addBalance(buyerId, order.getTotalAmount(), "ORDER_REFUND", order.getId(), "Người mua tự hủy đơn hàng (Mã: " + order.getOrderCode() + ")");

        orderStatusService.logStatusChange(order, oldStatus, "CANCELLED", buyerId, "Người mua đã chủ động hủy đơn hàng trước khi Shop tiếp nhận.");
    }


    @Transactional
    public void cancelProcessingOrder(Long sellerId, Long orderId, String cancelReason) {
        Order order = orderRepository.findById(orderId)
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
        walletService.addBalance(order.getUser().getId(), order.getTotalAmount(), "ORDER_REFUND", order.getId(), "Shop đã hủy đơn giữa chừng (Mã: " + order.getOrderCode() + ")");

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