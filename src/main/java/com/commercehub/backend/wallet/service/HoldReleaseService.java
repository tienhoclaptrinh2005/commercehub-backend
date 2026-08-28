package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.service.PlatformFeeLedgerService;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

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

    private static final String STATUS_HOLDING = "HOLDING";
    private static final String STATUS_COMPLAINED = "COMPLAINED";
    private static final String STATUS_WARRANTY_IN_PROGRESS = "WARRANTY_IN_PROGRESS";
    private static final String STATUS_WAITING_BUYER_CONFIRMATION = "WAITING_BUYER_CONFIRMATION";
    private static final String STATUS_DISPUTED = "DISPUTED";
    private static final String STATUS_REFUNDED = "REFUNDED";

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
     * COMPLAINED / WARRANTY_IN_PROGRESS / WAITING_BUYER_CONFIRMATION / DISPUTED
     * không được phép nhả tiền.
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
    // HOLDING -> COMPLAINED
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
     * - chuyển trạng thái COMPLAINED
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

        if (!STATUS_HOLDING.equals(hr.getStatus())) {
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

        hr.setStatus(STATUS_COMPLAINED);
        hr.setComplaintReason(reason);
        hr.setComplainedAt(now);
        hr.setRemainingHoldSeconds(remainingSeconds);

        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {} chuyển HOLDING -> COMPLAINED, remainingHoldSeconds={}",
                orderItemId,
                remainingSeconds
        );
    }

    // =========================================================
    // 2. SELLER START WARRANTY
    //
    // COMPLAINED -> WARRANTY_IN_PROGRESS
    // =========================================================

    /**
     * Seller chấp nhận xử lý bảo hành.
     *
     * Chỉ được gọi khi HoldRelease đang COMPLAINED.
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

        if (!STATUS_COMPLAINED.equals(hr.getStatus())) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_NOT_COMPLAINED
            );
        }

        validateRemainingHoldSeconds(hr);

        hr.setStatus(STATUS_WARRANTY_IN_PROGRESS);
        hr.setWarrantyStartedAt(OffsetDateTime.now());

        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {} chuyển COMPLAINED -> WARRANTY_IN_PROGRESS",
                orderItemId
        );
    }

    // =========================================================
    // 3. SELLER COMPLETE WARRANTY
    //
    // WARRANTY_IN_PROGRESS -> WAITING_BUYER_CONFIRMATION
    // =========================================================

    /**
     * Seller hoàn thành bảo hành.
     *
     * Sau khi hoàn thành:
     *
     * WARRANTY_IN_PROGRESS -> WAITING_BUYER_CONFIRMATION.
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

        if (!STATUS_WARRANTY_IN_PROGRESS.equals(hr.getStatus())) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_NOT_WARRANTY
            );
        }

        validateRemainingHoldSeconds(hr);
        hr.setStatus(STATUS_WAITING_BUYER_CONFIRMATION);

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

        if (!STATUS_WAITING_BUYER_CONFIRMATION.equals(hr.getStatus())) {
            throw new AppException(ErrorCode.HOLD_RELEASE_INVALID_STATUS);
        }

        long remainingSeconds = validateRemainingHoldSeconds(hr);
        hr.setStatus(STATUS_HOLDING);
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

        boolean canWithdraw =
                STATUS_COMPLAINED.equals(hr.getStatus())
                        || STATUS_WARRANTY_IN_PROGRESS.equals(hr.getStatus());

        if (!canWithdraw) {
            throw new AppException(ErrorCode.HOLD_RELEASE_INVALID_STATUS);
        }

        long remainingSeconds = validateRemainingHoldSeconds(hr);
        String oldStatus = hr.getStatus();
        hr.setStatus(STATUS_HOLDING);
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
    // COMPLAINED
    // hoặc
    // WARRANTY_IN_PROGRESS
    //
    // ->
    //
    // DISPUTED
    // =========================================================

    /**
     * Leo thang khiếu nại thành tranh chấp cần Admin xử lý.
     *
     * Cho phép từ:
     * - COMPLAINED
     * - WARRANTY_IN_PROGRESS
     * - WAITING_BUYER_CONFIRMATION
     *
     * Không cho phép trực tiếp:
     * HOLDING -> DISPUTED
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

        boolean canEscalate =
                STATUS_COMPLAINED.equals(hr.getStatus())
                        ||
                        STATUS_WARRANTY_IN_PROGRESS.equals(hr.getStatus())
                        ||
                        STATUS_WAITING_BUYER_CONFIRMATION.equals(hr.getStatus());

        if (!canEscalate) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_INVALID_STATUS
            );
        }

        validateRemainingHoldSeconds(hr);

        String oldStatus = hr.getStatus();

        hr.setStatus(STATUS_DISPUTED);

        holdReleaseRepository.save(hr);

        log.info(
                "OrderItem {} chuyển {} -> DISPUTED",
                orderItemId,
                oldStatus
        );
    }

    // =========================================================
    // 5. ADMIN RESOLVE DISPUTE
    //
    // DISPUTED
    //
    // BUYER WIN  -> REFUNDED
    // SELLER WIN -> HOLDING
    // =========================================================

    /**
     * Admin phán quyết tranh chấp.
     *
     * BUYER WIN:
     *
     * DISPUTED -> REFUNDED
     *
     * - gỡ hold của seller
     * - hoàn tiền buyer
     * - FeeLedger -> CANCELLED
     *
     *
     * SELLER WIN:
     *
     * DISPUTED -> HOLDING
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

        if (!STATUS_DISPUTED.equals(hr.getStatus())) {
            throw new AppException(
                    ErrorCode.HOLD_RELEASE_NOT_DISPUTED
            );
        }

        // =====================================================
        // BUYER WIN
        // =====================================================

        if (isBuyerWin) {

            /*
             * Gỡ tiền đang hold trong ví seller.
             *
             * refId = HoldRelease ID
             * để đảm bảo mỗi HoldRelease được xử lý riêng.
             */
            walletService.systemCancelSellerHold(
                    sellerId,
                    hr.getHoldAmount(),
                    hr.getId()
            );

            /*
             * Hoàn tiền lại ví khả dụng của buyer.
             */
            walletService.systemCreditBalance(
                    buyerId,
                    hr.getHoldAmount(),
                    "DISPUTE_REFUND",
                    hr.getId(),
                    "HOLD_RELEASE"
            );

            /*
             * Buyer thắng:
             * giao dịch không hoàn tất thành công,
             * platform không thu phí.
             *
             * Không sử dụng WAIVED.
             */
            if (hr.getFeeLedgerId() != null) {

                platformFeeLedgerService.markAsCancelled(
                        hr.getFeeLedgerId(),
                        "Buyer thắng dispute - Order ID " + orderId,
                        null
                );
            }

            hr.setStatus(STATUS_REFUNDED);

            holdReleaseRepository.save(hr);
            markOrderItemRefunded(hr.getOrderItemId());
            if (productRepository.incrementFailedDisputeCountByOrderItemId(hr.getOrderItemId()) != 1) {
                log.warn("Không cập nhật được failed_dispute_count cho orderItem {}", hr.getOrderItemId());
            }

            log.info(
                    "HoldRelease {}: BUYER thắng dispute, hoàn {} cho Buyer ID {}",
                    holdReleaseId,
                    hr.getHoldAmount(),
                    buyerId
            );

            return;
        }

        // =====================================================
        // SELLER WIN
        // =====================================================

        long remainingSeconds =
                validateRemainingHoldSeconds(hr);

        hr.setStatus(STATUS_HOLDING);

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
            order.setPaymentStatus("REFUNDED");
            order.setStatus("REFUNDED");
        } else {
            // Mỗi item hoàn 100%; PARTIAL_REFUND chỉ mô tả order nhiều item.
            order.setPaymentStatus("PARTIAL_REFUND");
        }
        orderRepository.save(order);
    }
}
