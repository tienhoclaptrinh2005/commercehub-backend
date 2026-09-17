package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.service.PlatformFeeLedgerService;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.entity.OrderPaymentStatus;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.entity.HoldReleaseStatus;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldReleaseService {

    private static final int MAX_BATCHES_PER_RUN = 20;

    // =========================================================
    // HOLD RELEASE STATUS
    // =========================================================

    private final HoldReleaseRepository holdReleaseRepository;
    private final HoldReleaseProcessor holdReleaseProcessor;
    private final PlatformFeeLedgerService platformFeeLedgerService;
    private final WalletService walletService;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Value("${commercehub.jobs.batch-size:200}")
    private int batchSize;

    // =========================================================
    // SCHEDULER
    // =========================================================

    /**
     * Xử lý các HoldRelease đã đến hạn.
     *
     * Repository bắt buộc chỉ trả:
     * status = HOLDING
     * scheduledReleaseAt <= now
     *
     * FROZEN không được phép nhả tiền. Trạng thái chi tiết của khiếu nại
     * được quản lý riêng trong OrderDispute.
     */
    public void processDueReleases(OffsetDateTime now) {

        int safeBatchSize = Math.min(Math.max(batchSize, 1), 500);
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Long> dueIds = holdReleaseRepository.findDueReleaseIds(
                    now,
                    PageRequest.of(0, safeBatchSize)
            );
            if (dueIds.isEmpty()) {
                return;
            }

            for (Long holdReleaseId : dueIds) {
                try {
                    holdReleaseProcessor.processSingle(holdReleaseId);
                } catch (Exception e) {
                    log.error(
                            "Lỗi khi xử lý HoldRelease ID {}: {}",
                            holdReleaseId,
                            e.getMessage(),
                            e
                    );
                }
            }
        }
        log.warn("Hold release job đạt giới hạn {} batch; phần còn lại xử lý ở lượt sau", MAX_BATCHES_PER_RUN);
    }

    // =========================================================
    // 1. BUYER COMPLAIN
    //
    // HOLDING -> FROZEN
    // =========================================================

    /**
     * Buyer tạo khiếu nại cho một OrderItem.
     *
     * Khiếu nại chỉ được phép khi:
     * - HoldRelease đang HOLDING
     * - T+7 chưa hết hạn
     *
     * Khi complain:
     * - lưu complaintReason
     * - lưu complainedAt
     * - lưu remainingHoldSeconds
     * - chuyển trạng thái FROZEN
     *
     * remainingHoldSeconds dùng để tạm dừng đồng hồ T+7.
     */
    @Transactional
    public void complain(
            Long orderItemId,
            String reason
    ) {

        HoldRelease hr = holdReleaseRepository
                .findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.HOLD_RELEASE_NOT_FOUND
                        )
                );

        if (hr.getStatus() != HoldReleaseStatus.HOLDING) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_INVALID_STATUS
            );
        }

        if (hr.getScheduledReleaseAt() == null) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_INVALID_STATUS
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        long remainingSeconds = Duration.between(
                now,
                hr.getScheduledReleaseAt()
        ).getSeconds();

        /*
         * Nếu <= 0:
         * T+7 đã hết hoặc đúng thời điểm scheduler có thể release.
         *
         * PESSIMISTIC_WRITE đảm bảo complain và processor
         * không cùng sửa một HoldRelease tại một thời điểm.
         */
        if (remainingSeconds <= 0) {
            throw new AppException(
                    ErrorCode.COMPLAINT_NOT_ALLOWED
            );
        }

        hr.setStatus(HoldReleaseStatus.FROZEN);
        hr.setComplaintReason(reason);
        hr.setComplainedAt(now);
        hr.setRemainingHoldSeconds(remainingSeconds);

        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {} chuyển HOLDING -> FROZEN, remainingHoldSeconds={}",
                orderItemId,
                remainingSeconds
        );
    }

    // =========================================================
    // 2. SELLER START WARRANTY
    //
    // HoldRelease tiếp tục FROZEN; OrderDispute chuyển WARRANTY_IN_PROGRESS
    // =========================================================

    /**
     * Seller chấp nhận xử lý bảo hành.
     *
     * Chỉ được gọi khi HoldRelease đang FROZEN.
     */
    @Transactional
    public void startWarranty(Long orderItemId) {

        HoldRelease hr = holdReleaseRepository
                .findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.HOLD_RELEASE_NOT_FOUND
                        )
                );

        if (hr.getStatus() != HoldReleaseStatus.FROZEN) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_NOT_FROZEN
            );
        }

        validateRemainingHoldSeconds(hr);

        hr.setStatus(HoldReleaseStatus.FROZEN);
        hr.setWarrantyStartedAt(OffsetDateTime.now());

        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {} vẫn FROZEN khi seller bắt đầu bảo hành",
                orderItemId
        );
    }

    // =========================================================
    // 3. SELLER COMPLETE WARRANTY
    //
    // HoldRelease tiếp tục FROZEN; OrderDispute chờ buyer xác nhận
    // =========================================================

    /**
     * Seller hoàn thành bảo hành.
     *
     * Sau khi hoàn thành:
     *
     * HoldRelease vẫn FROZEN trong lúc chờ buyer xác nhận.
     * Đồng hồ T+7 chỉ tiếp tục khi buyer đồng ý hoặc hết hạn phản hồi.
     */
    @Transactional
    public void markWarrantyAwaitingBuyer(Long orderItemId) {

        HoldRelease hr = holdReleaseRepository
                .findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.HOLD_RELEASE_NOT_FOUND
                        )
                );

        if (hr.getStatus() != HoldReleaseStatus.FROZEN) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_NOT_FROZEN
            );
        }

        validateRemainingHoldSeconds(hr);
        hr.setStatus(HoldReleaseStatus.FROZEN);

        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {}: seller báo xử lý xong, chờ buyer xác nhận",
                orderItemId
        );
    }

    /** Buyer đồng ý, hoặc hết hạn xác nhận: tiếp tục đồng hồ T+7 còn lại. */
    @Transactional
    public void confirmWarrantyResolved(Long orderItemId) {
        HoldRelease hr = holdReleaseRepository
                .findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(() -> new AppException(ErrorCode.HOLD_RELEASE_NOT_FOUND));

        if (hr.getStatus() != HoldReleaseStatus.FROZEN) {
            throw new AppException(ErrorCode.HOLD_RELEASE_INVALID_STATUS);
        }

        long remainingSeconds = validateRemainingHoldSeconds(hr);
        hr.setStatus(HoldReleaseStatus.HOLDING);
        hr.setScheduledReleaseAt(OffsetDateTime.now().plusSeconds(remainingSeconds));
        hr.setRemainingHoldSeconds(null);
        holdReleaseRepository.save(hr);
    }

    /**
     * Buyer tự hủy khiếu nại: khôi phục đồng hồ T+7 còn lại.
     * Chỉ chấp nhận trước khi seller phản hồi hoặc trong lúc seller bảo hành.
     */
    @Transactional
    public void withdrawComplaint(Long orderItemId) {
        HoldRelease hr = holdReleaseRepository
                .findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(() -> new AppException(ErrorCode.HOLD_RELEASE_NOT_FOUND));

        if (hr.getStatus() != HoldReleaseStatus.FROZEN) {
            throw new AppException(ErrorCode.HOLD_RELEASE_INVALID_STATUS);
        }

        long remainingSeconds = validateRemainingHoldSeconds(hr);
        HoldReleaseStatus oldStatus = hr.getStatus();
        hr.setStatus(HoldReleaseStatus.HOLDING);
        hr.setScheduledReleaseAt(OffsetDateTime.now().plusSeconds(remainingSeconds));
        hr.setRemainingHoldSeconds(null);
        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {}: buyer hủy khiếu nại, {} -> HOLDING trong {} giây",
                orderItemId,
                oldStatus,
                remainingSeconds
        );
    }

    // =========================================================
    // 4. ESCALATE TO DISPUTE
    //
    // HoldRelease tiếp tục FROZEN; OrderDispute chuyển ADMIN_REVIEW
    // =========================================================

    /**
     * Leo thang khiếu nại thành tranh chấp cần Admin xử lý.
     *
     * Chỉ cho phép khi khoản tiền đã FROZEN bởi một khiếu nại hợp lệ.
     */
    @Transactional
    public void escalateDispute(Long orderItemId) {

        HoldRelease hr = holdReleaseRepository
                .findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.HOLD_RELEASE_NOT_FOUND
                        )
                );

        if (hr.getStatus() != HoldReleaseStatus.FROZEN) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_INVALID_STATUS
            );
        }

        validateRemainingHoldSeconds(hr);

        HoldReleaseStatus oldStatus = hr.getStatus();

        hr.setStatus(HoldReleaseStatus.FROZEN);

        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {} giữ trạng thái {} khi khiếu nại chuyển ADMIN_REVIEW",
                orderItemId,
                oldStatus
        );
    }

    /**
     * Seller chủ động hoàn tiền cho OrderItem đang khiếu nại.
     * PostgreSQL vẫn là nguồn dữ liệu chính; toàn bộ cập nhật ví, hold, phí và
     * trạng thái hoàn của item được thực hiện trong cùng transaction.
     *
     * @return số tiền thực tế đã hoàn cho Buyer
     */
    @Transactional
    public BigDecimal refundDisputedItemBySeller(
            Long orderItemId,
            Long buyerId,
            Long sellerId,
            Long orderId
    ) {
        HoldRelease holdRelease = holdReleaseRepository
                .findByOrderItemIdWithLock(orderItemId)
                .orElseThrow(() -> new AppException(ErrorCode.HOLD_RELEASE_NOT_FOUND));

        if (holdRelease.getStatus() != HoldReleaseStatus.FROZEN) {
            throw new AppException(ErrorCode.HOLD_RELEASE_NOT_FROZEN);
        }

        refundBuyer(
                holdRelease,
                buyerId,
                sellerId,
                "Seller chủ động hoàn tiền dispute - Order ID " + orderId
        );
        return holdRelease.getHoldAmount();
    }

    // =========================================================
    // 5. ADMIN RESOLVE DISPUTE
    //
    // FROZEN
    //
    // BUYER WIN  -> REFUNDED
    // SELLER WIN -> HOLDING
    // =========================================================

    /**
     * Admin phán quyết tranh chấp.
     *
     * BUYER WIN:
     *
     * FROZEN -> REFUNDED
     *
     * - gỡ hold của seller
     * - hoàn tiền buyer
     * - FeeLedger -> CANCELLED
     *
     *
     * SELLER WIN:
     *
     * FROZEN -> HOLDING
     *
     * - không hoàn buyer
     * - tiếp tục phần thời gian T+7 còn lại
     */
    @Transactional
    public void resolveDispute(
            Long holdReleaseId,
            boolean isBuyerWin,
            Long buyerId,
            Long sellerId,
            Long orderId
    ) {

        HoldRelease hr = holdReleaseRepository
                .findByIdWithLock(holdReleaseId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.HOLD_RELEASE_NOT_FOUND
                        )
                );

        if (hr.getStatus() != HoldReleaseStatus.FROZEN) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_NOT_FROZEN
            );
        }

        // =====================================================
        // BUYER WIN
        // =====================================================

        if (isBuyerWin) {
            refundBuyer(
                    hr,
                    buyerId,
                    sellerId,
                    "Buyer thắng dispute - Order ID " + orderId
            );
            return;
        }

        // =====================================================
        // SELLER WIN
        // =====================================================

        long remainingSeconds =
                validateRemainingHoldSeconds(hr);

        hr.setStatus(HoldReleaseStatus.HOLDING);

        hr.setScheduledReleaseAt(
                OffsetDateTime.now()
                        .plusSeconds(remainingSeconds)
        );

        /*
         * Timer đã phục hồi vào scheduledReleaseAt.
         */
        hr.setRemainingHoldSeconds(null);

        holdReleaseRepository.save(hr);

        log.info(
                "HoldRelease {}: SELLER thắng dispute, tiếp tục HOLDING trong {} giây",
                holdReleaseId,
                remainingSeconds
        );
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private void refundBuyer(
            HoldRelease holdRelease,
            Long buyerId,
            Long sellerId,
            String feeCancellationReason
    ) {
        /*
         * refId = HoldRelease ID giúp các bút toán ví giữ tính idempotent cho
         * từng OrderItem, kể cả khi request bị retry.
         */
        walletService.systemCancelSellerHold(
                sellerId,
                holdRelease.getHoldAmount(),
                holdRelease.getId()
        );

        walletService.systemCreditBalance(
                buyerId,
                holdRelease.getHoldAmount(),
                "DISPUTE_REFUND",
                holdRelease.getId(),
                "HOLD_RELEASE"
        );

        if (holdRelease.getFeeLedgerId() != null) {
            platformFeeLedgerService.markAsCancelled(
                    holdRelease.getFeeLedgerId(),
                    feeCancellationReason,
                    null
            );
        }

        holdRelease.setStatus(HoldReleaseStatus.REFUNDED);
        holdReleaseRepository.save(holdRelease);
        markOrderItemRefunded(holdRelease.getOrderItemId());

        if (productRepository.incrementFailedDisputeCountByOrderItemId(holdRelease.getOrderItemId()) != 1) {
            log.warn("Không cập nhật được failed_dispute_count cho orderItem {}", holdRelease.getOrderItemId());
        }

        log.info(
                "HoldRelease {}: hoàn {} cho Buyer ID {} từ dispute",
                holdRelease.getId(),
                holdRelease.getHoldAmount(),
                buyerId
        );
    }

    /**
     * remainingHoldSeconds phải tồn tại và > 0
     * trong flow complaint / warranty / dispute.
     *
     * Nếu không có thì dữ liệu HoldRelease đang không nhất quán.
     */
    private long validateRemainingHoldSeconds(HoldRelease hr) {

        Long remainingSeconds =
                hr.getRemainingHoldSeconds();

        if (remainingSeconds == null || remainingSeconds <= 0) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_INVALID_STATUS
            );
        }

        return remainingSeconds;
    }

    private void markOrderItemRefunded(Long orderItemId) {
        OrderItem item = orderItemRepository.findByIdWithLock(orderItemId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"REFUNDED".equals(item.getRefundStatus())) {
            item.setRefundStatus("REFUNDED");
            item.setRefundedAt(OffsetDateTime.now());
            orderItemRepository.save(item);
        }

        Order order = orderRepository.findByIdWithLock(item.getOrder().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        long totalItems = orderItemRepository.countByOrderId(order.getId());
        long refundedItems = orderItemRepository.countByOrderIdAndRefundStatus(order.getId(), "REFUNDED");

        if (totalItems > 0 && totalItems == refundedItems) {
            order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
        } else {
            // Mỗi item hoàn 100%; PARTIALLY_REFUNDED chỉ mô tả order nhiều item.
            order.setPaymentStatus(OrderPaymentStatus.PARTIALLY_REFUNDED);
        }
        orderRepository.save(order);
    }
}
