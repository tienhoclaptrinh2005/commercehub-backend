package com.commercehub.backend.dispute.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dispute.dto.request.CreateDisputeRequest;
import com.commercehub.backend.dispute.dto.request.SellerRespondRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.entity.OrderDispute;
import com.commercehub.backend.dispute.mapper.DisputeMapper;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.wallet.service.HoldReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final OrderDisputeRepository disputeRepository;
    private final DisputeMapper disputeMapper;
    private final HoldReleaseService holdReleaseService;

    /*
     * schema bắt buộc deadline_at NOT NULL.
     *
     * Tài liệu hiện tại chưa chốt chính xác thời hạn dispute.
     * Vì vậy để config thay vì hard-code trong logic.
     */
    @Value("${commercehub.dispute.seller-response-hours:24}")
    private long sellerResponseHours;

    @Value("${commercehub.dispute.buyer-confirmation-hours:24}")
    private long buyerConfirmationHours;

    @Value("${commercehub.dispute.warranty-processing-hours:24}")
    private long warrantyProcessingHours;

    @Value("${commercehub.dispute.admin-review-hours:72}")
    private long adminReviewHours;

    // =========================================================
    // BUYER COMPLAIN
    // =========================================================

    @Transactional
    public DisputeResponse createComplaint(
            Long buyerId,
            Long orderId,
            Long orderItemId,
            CreateDisputeRequest request
    ) {

        boolean owner =
                disputeRepository.buyerOwnsOrderItem(
                        buyerId,
                        orderId,
                        orderItemId
                );

        if (!owner) {
            throw new AppException(
                    ErrorCode.ORDER_ACCESS_DENIED
            );
        }

        /*
         * Database cũng có UNIQUE(order_item_id),
         * nhưng check trước để trả message rõ hơn.
         */
        if (
                disputeRepository
                        .existsByOrderItemId(orderItemId)
        ) {

            throw new AppException(
                    ErrorCode.DISPUTE_ALREADY_EXISTS
            );
        }

        /*
         * HOLDING -> COMPLAINED
         *
         * HoldReleaseService dùng PESSIMISTIC LOCK.
         */
        holdReleaseService.complain(
                orderItemId,
                request.reason()
        );

        Long shopId = disputeRepository
                .findShopId(orderId, orderItemId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.SHOP_NOT_FOUND
                        )
                );

        OffsetDateTime now =
                OffsetDateTime.now();

        OrderDispute dispute =
                OrderDispute.builder()
                        .orderId(orderId)
                        .orderItemId(orderItemId)
                        .userId(buyerId)
                        .shopId(shopId)
                        .reason(request.reason())
                        .evidenceUrls(
                                disputeMapper.toArray(
                                        request.evidenceUrls()
                                )
                        )
                        .status(
                                OrderDispute.STATUS_OPEN
                        )
                        .deadlineAt(
                                now.plusHours(
                                        sellerResponseHours
                                )
                        )
                        .build();

        dispute =
                disputeRepository.save(dispute);

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // BUYER ESCALATE
    // =========================================================

    @Transactional
    public DisputeResponse escalateByBuyer(
            Long buyerId,
            Long disputeId
    ) {

        OrderDispute dispute =
                disputeRepository
                        .findByIdWithLock(disputeId)
                        .orElseThrow(
                                () -> new AppException(
                                        ErrorCode.DISPUTE_NOT_FOUND
                                )
                        );

        if (!dispute.getUserId().equals(buyerId)) {
            throw new AppException(
                    ErrorCode.DISPUTE_ACCESS_DENIED
            );
        }

        requireStatus(dispute, OrderDispute.STATUS_WAITING_BUYER_CONFIRMATION);
        requireDeadlineActive(
                dispute,
                OffsetDateTime.now(),
                ErrorCode.DISPUTE_BUYER_CONFIRMATION_DEADLINE_EXPIRED
        );

        /*
         * COMPLAINED / WARRANTY_IN_PROGRESS
         * ->
         * DISPUTED
         */
        holdReleaseService.escalateDispute(
                dispute.getOrderItemId()
        );

        dispute.setStatus(
                OrderDispute.STATUS_PROCESSING
        );

        /*
         * Khi chính thức lên Admin,
         * reset deadline tính từ thời điểm escalation.
         */
        dispute.setDeadlineAt(
                OffsetDateTime.now()
                        .plusHours(
                                adminReviewHours
                        )
        );

        disputeRepository.save(dispute);

        disputeRepository.linkFeeLedgerToDispute(
                dispute.getOrderItemId(),
                dispute.getId()
        );

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // BUYER WITHDRAW
    // =========================================================

    /**
     * Buyer tự hủy khiếu nại khi seller chưa phản hồi hoặc đang bảo hành.
     * Hồ sơ được đóng vĩnh viễn vì mỗi OrderItem chỉ có một dispute.
     */
    @Transactional
    public DisputeResponse withdrawByBuyer(
            Long buyerId,
            Long disputeId
    ) {

        OrderDispute dispute = disputeRepository
                .findByIdWithLock(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_FOUND));

        if (!dispute.getUserId().equals(buyerId)) {
            throw new AppException(ErrorCode.DISPUTE_ACCESS_DENIED);
        }

        boolean canWithdraw =
                OrderDispute.STATUS_OPEN.equals(dispute.getStatus())
                        || OrderDispute.STATUS_WARRANTY_IN_PROGRESS.equals(dispute.getStatus());

        if (!canWithdraw) {
            throw new AppException(ErrorCode.DISPUTE_WITHDRAW_NOT_ALLOWED);
        }

        ErrorCode deadlineError = OrderDispute.STATUS_OPEN.equals(dispute.getStatus())
                ? ErrorCode.DISPUTE_SELLER_RESPONSE_DEADLINE_EXPIRED
                : ErrorCode.DISPUTE_WARRANTY_DEADLINE_EXPIRED;
        requireDeadlineActive(dispute, OffsetDateTime.now(), deadlineError);

        holdReleaseService.withdrawComplaint(dispute.getOrderItemId());

        dispute.setStatus(OrderDispute.STATUS_CLOSED);
        dispute.setClosedReason(OrderDispute.CLOSED_REASON_BUYER_WITHDREW);
        dispute.setResolvedAt(OffsetDateTime.now());
        disputeRepository.save(dispute);

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // SELLER START WARRANTY
    // =========================================================

    @Transactional
    public DisputeResponse startWarranty(
            Long sellerId,
            Long orderId,
            Long orderItemId,
            SellerRespondRequest request
    ) {

        validateSellerOwnership(
                sellerId,
                orderId,
                orderItemId
        );

        OrderDispute dispute =
                getByOrderItemWithLock(orderItemId);

        requireOpen(dispute);
        requireDeadlineActive(
                dispute,
                OffsetDateTime.now(),
                ErrorCode.DISPUTE_SELLER_RESPONSE_DEADLINE_EXPIRED
        );

        /*
         * COMPLAINED -> WARRANTY_IN_PROGRESS
         */
        holdReleaseService.startWarranty(
                orderItemId
        );

        dispute.setStatus(OrderDispute.STATUS_WARRANTY_IN_PROGRESS);
        dispute.setDeadlineAt(
                OffsetDateTime.now().plusHours(warrantyProcessingHours)
        );

        applySellerResponse(
                dispute,
                request
        );

        disputeRepository.save(dispute);

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // SELLER COMPLETE WARRANTY
    // =========================================================

    @Transactional
    public DisputeResponse completeWarranty(
            Long sellerId,
            Long orderId,
            Long orderItemId,
            SellerRespondRequest request
    ) {

        validateSellerOwnership(
                sellerId,
                orderId,
                orderItemId
        );

        OrderDispute dispute =
                getByOrderItemWithLock(orderItemId);

        requireStatus(dispute, OrderDispute.STATUS_WARRANTY_IN_PROGRESS);
        requireDeadlineActive(
                dispute,
                OffsetDateTime.now(),
                ErrorCode.DISPUTE_WARRANTY_DEADLINE_EXPIRED
        );

        /*
         * Seller chỉ báo đã xử lý; buyer phải xác nhận trước khi T+7 chạy tiếp.
         */
        holdReleaseService.markWarrantyAwaitingBuyer(
                orderItemId
        );

        applySellerResponse(
                dispute,
                request
        );

        dispute.setStatus(
                OrderDispute.STATUS_WAITING_BUYER_CONFIRMATION
        );
        dispute.setDeadlineAt(OffsetDateTime.now().plusHours(buyerConfirmationHours));
        dispute.setResolvedAt(null);

        disputeRepository.save(dispute);

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // BUYER CONFIRM WARRANTY
    // =========================================================

    @Transactional
    public DisputeResponse confirmWarrantyByBuyer(Long buyerId, Long disputeId) {
        OrderDispute dispute = disputeRepository.findByIdWithLock(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_FOUND));

        if (!dispute.getUserId().equals(buyerId)) {
            throw new AppException(ErrorCode.DISPUTE_ACCESS_DENIED);
        }

        requireStatus(dispute, OrderDispute.STATUS_WAITING_BUYER_CONFIRMATION);
        requireDeadlineActive(
                dispute,
                OffsetDateTime.now(),
                ErrorCode.DISPUTE_BUYER_CONFIRMATION_DEADLINE_EXPIRED
        );
        holdReleaseService.confirmWarrantyResolved(dispute.getOrderItemId());
        dispute.setStatus(OrderDispute.STATUS_CLOSED);
        dispute.setClosedReason(OrderDispute.CLOSED_REASON_BUYER_ACCEPTED_WARRANTY);
        dispute.setResolvedAt(OffsetDateTime.now());
        disputeRepository.save(dispute);
        return disputeMapper.toResponse(dispute);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeExpiredBuyerConfirmation(Long disputeId, OffsetDateTime now) {
        OrderDispute dispute = disputeRepository.findByIdWithLock(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_FOUND));

        if (!OrderDispute.STATUS_WAITING_BUYER_CONFIRMATION.equals(dispute.getStatus())
                || dispute.getDeadlineAt().isAfter(now)) {
            return;
        }

        holdReleaseService.confirmWarrantyResolved(dispute.getOrderItemId());
        dispute.setStatus(OrderDispute.STATUS_CLOSED);
        dispute.setClosedReason(OrderDispute.CLOSED_REASON_BUYER_CONFIRMATION_TIMEOUT);
        dispute.setAdminNote("Hệ thống đóng do buyer không phản hồi đúng hạn");
        dispute.setResolvedAt(now);
        disputeRepository.save(dispute);
    }

    // =========================================================
    // SELLER ESCALATE
    // =========================================================

    @Transactional
    public DisputeResponse escalateBySeller(
            Long sellerId,
            Long orderId,
            Long orderItemId,
            SellerRespondRequest request
    ) {

        validateSellerOwnership(
                sellerId,
                orderId,
                orderItemId
        );

        OrderDispute dispute =
                getByOrderItemWithLock(orderItemId);

        requireAnyStatus(
                dispute,
                OrderDispute.STATUS_OPEN,
                OrderDispute.STATUS_WARRANTY_IN_PROGRESS
        );

        ErrorCode deadlineError = OrderDispute.STATUS_OPEN.equals(dispute.getStatus())
                ? ErrorCode.DISPUTE_SELLER_RESPONSE_DEADLINE_EXPIRED
                : ErrorCode.DISPUTE_WARRANTY_DEADLINE_EXPIRED;
        requireDeadlineActive(dispute, OffsetDateTime.now(), deadlineError);

        /*
         * Seller từ chối bảo hành
         * hoặc không thể giải quyết:
         *
         * COMPLAINED / WARRANTY_IN_PROGRESS
         * ->
         * DISPUTED
         */
        holdReleaseService.escalateDispute(
                orderItemId
        );

        applySellerResponse(
                dispute,
                request
        );

        dispute.setStatus(
                OrderDispute.STATUS_PROCESSING
        );

        dispute.setDeadlineAt(
                OffsetDateTime.now()
                        .plusHours(
                                adminReviewHours
                        )
        );

        disputeRepository.save(dispute);

        disputeRepository.linkFeeLedgerToDispute(
                orderItemId,
                dispute.getId()
        );

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // BUYER GET DETAIL
    // =========================================================

    @Transactional(readOnly = true)
    public DisputeResponse getBuyerDispute(
            Long buyerId,
            Long disputeId
    ) {

        OrderDispute dispute =
                disputeRepository
                        .findById(disputeId)
                        .orElseThrow(
                                () -> new AppException(
                                        ErrorCode.DISPUTE_NOT_FOUND
                                )
                        );

        if (!dispute.getUserId().equals(buyerId)) {

            throw new AppException(
                    ErrorCode.DISPUTE_ACCESS_DENIED
            );
        }

        return disputeMapper.toResponse(dispute);
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> getBuyerDisputes(
            Long buyerId,
            Pageable pageable
    ) {

        return disputeRepository
                .findByUserIdOrderByCreatedAtDesc(
                        buyerId,
                        limitPageable(pageable)
                )
                .map(disputeMapper::toResponse);
    }

    // =========================================================
    // SELLER LIST / DETAIL
    // =========================================================

    @Transactional(readOnly = true)
    public Page<DisputeResponse> getSellerDisputes(
            Long sellerId,
            Pageable pageable
    ) {

        /*
         * 1 User = 1 Shop theo schema.
         */
        Long shopId =
                findSellerShopId(sellerId);

        return disputeRepository
                .findByShopIdOrderByCreatedAtDesc(
                        shopId,
                        limitPageable(pageable)
                )
                .map(disputeMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DisputeResponse getSellerDispute(
            Long sellerId,
            Long disputeId
    ) {

        OrderDispute dispute =
                disputeRepository
                        .findById(disputeId)
                        .orElseThrow(
                                () -> new AppException(
                                        ErrorCode.DISPUTE_NOT_FOUND
                                )
                        );

        Long ownerId =
                disputeRepository
                        .findShopOwnerId(
                                dispute.getShopId()
                        )
                        .orElseThrow(
                                () -> new AppException(
                                        ErrorCode.SHOP_NOT_FOUND
                                )
                        );

        if (!ownerId.equals(sellerId)) {

            throw new AppException(
                    ErrorCode.DISPUTE_ACCESS_DENIED
            );
        }

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // PRIVATE
    // =========================================================

    private OrderDispute getByOrderItemWithLock(
            Long orderItemId
    ) {

        return disputeRepository
                .findByOrderItemIdWithLock(
                        orderItemId
                )
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.DISPUTE_NOT_FOUND
                        )
                );
    }

    private void validateSellerOwnership(
            Long sellerId,
            Long orderId,
            Long orderItemId
    ) {

        boolean owner =
                disputeRepository
                        .sellerOwnsOrderItem(
                                sellerId,
                                orderId,
                                orderItemId
                        );

        if (!owner) {

            throw new AppException(
                    ErrorCode.ORDER_ACCESS_DENIED
            );
        }
    }

    private void requireOpen(
            OrderDispute dispute
    ) {

        if (
                !OrderDispute.STATUS_OPEN
                        .equals(dispute.getStatus())
        ) {

            throw new AppException(
                    ErrorCode.DISPUTE_INVALID_STATUS
            );
        }
    }

    private void requireStatus(OrderDispute dispute, String expectedStatus) {
        if (!expectedStatus.equals(dispute.getStatus())) {
            throw new AppException(ErrorCode.DISPUTE_INVALID_STATUS);
        }
    }

    private void requireAnyStatus(OrderDispute dispute, String... expectedStatuses) {
        for (String expectedStatus : expectedStatuses) {
            if (expectedStatus.equals(dispute.getStatus())) {
                return;
            }
        }
        throw new AppException(ErrorCode.DISPUTE_INVALID_STATUS);
    }

    private void requireDeadlineActive(
            OrderDispute dispute,
            OffsetDateTime now,
            ErrorCode errorCode
    ) {
        if (!dispute.getDeadlineAt().isAfter(now)) {
            throw new AppException(errorCode);
        }
    }

    private Pageable limitPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), 100);
        return PageRequest.of(page, size, pageable.getSort());
    }

    private void applySellerResponse(
            OrderDispute dispute,
            SellerRespondRequest request
    ) {

        if (request == null) {
            return;
        }

        if (
                request.response() != null
                        &&
                        !request.response().isBlank()
        ) {

            dispute.setShopResponse(
                    request.response()
            );
        }

        if (
                request.evidenceUrls() != null
                        &&
                        !request.evidenceUrls().isEmpty()
        ) {

            dispute.setShopEvidenceUrls(
                    disputeMapper.toArray(
                            request.evidenceUrls()
                    )
            );
        }
    }

    /*
     * Không cần thêm ShopRepository chỉ vì query này.
     *
     * Ta sẽ thêm method repository phía dưới.
     */
    private Long findSellerShopId(
            Long sellerId
    ) {

        return disputeRepository
                .findShopIdByOwnerId(sellerId)
                .orElseThrow(
                        () -> new AppException(
                                ErrorCode.SHOP_NOT_FOUND
                        )
                );
    }
}
