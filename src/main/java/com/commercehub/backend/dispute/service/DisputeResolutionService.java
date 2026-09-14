package com.commercehub.backend.dispute.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dispute.dto.request.AdminResolveDisputeRequest;
import com.commercehub.backend.dispute.dto.response.AdminDisputeSummaryResponse;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.entity.OrderDispute;
import com.commercehub.backend.dispute.entity.DisputeResolution;
import com.commercehub.backend.dispute.entity.DisputeResolvedBy;
import com.commercehub.backend.dispute.entity.DisputeStatus;
import com.commercehub.backend.dispute.mapper.DisputeMapper;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import com.commercehub.backend.wallet.service.HoldReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class DisputeResolutionService {

    private final OrderDisputeRepository disputeRepository;
    private final HoldReleaseRepository holdReleaseRepository;
    private final HoldReleaseService holdReleaseService;
    private final DisputeMapper disputeMapper;

    // =========================================================
    // ADMIN RESOLVE
    // =========================================================

    @Transactional
    public DisputeResponse resolve(
            Long adminId,
            Long disputeId,
            AdminResolveDisputeRequest request
    ) {

        if (request.decision() != DisputeResolution.BUYER_WIN
                && request.decision() != DisputeResolution.SELLER_WIN) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.resolutionNote() == null || request.resolutionNote().trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        OrderDispute dispute =
                disputeRepository
                        .findByIdWithLock(disputeId)
                        .orElseThrow(
                                () -> new AppException(
                                        ErrorCode.DISPUTE_NOT_FOUND
                                )
                        );

        if (dispute.getStatus() != DisputeStatus.ADMIN_REVIEW) {
            if (dispute.getStatus() == DisputeStatus.RESOLVED
                    && request.decision() == dispute.getResolution()) {
                return disputeMapper.toResponse(dispute);
            }
            throw new AppException(ErrorCode.DISPUTE_INVALID_STATUS);
        }

        return resolveLocked(
                dispute,
                adminId,
                DisputeResolvedBy.ADMIN,
                request.decision(),
                request.resolutionNote().trim()
        );
    }

    /** Seller không phản hồi OPEN quá hạn: hệ thống xử buyer thắng 100%. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolveExpiredOpenForBuyer(Long disputeId, OffsetDateTime now) {
        OrderDispute dispute = disputeRepository.findByIdWithLock(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_FOUND));

        if (dispute.getStatus() != DisputeStatus.OPEN
                || dispute.getDeadlineAt().isAfter(now)) {
            return;
        }

        holdReleaseService.escalateDispute(dispute.getOrderItemId());
        disputeRepository.linkFeeLedgerToDispute(dispute.getOrderItemId(), dispute.getId());
        resolveLocked(
                dispute,
                null,
                DisputeResolvedBy.SYSTEM,
                DisputeResolution.BUYER_WIN,
                "Hệ thống xử buyer thắng do seller không phản hồi trong thời hạn"
        );
    }

    /** Seller đã nhận bảo hành nhưng không hoàn tất đúng hạn: tự động hoàn 100% cho buyer. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolveExpiredWarrantyForBuyer(Long disputeId, OffsetDateTime now) {
        OrderDispute dispute = disputeRepository.findByIdWithLock(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_FOUND));

        if (dispute.getStatus() != DisputeStatus.WARRANTY_IN_PROGRESS
                || dispute.getDeadlineAt().isAfter(now)) {
            return;
        }

        holdReleaseService.escalateDispute(dispute.getOrderItemId());
        disputeRepository.linkFeeLedgerToDispute(dispute.getOrderItemId(), dispute.getId());
        resolveLocked(
                dispute,
                null,
                DisputeResolvedBy.SYSTEM,
                DisputeResolution.BUYER_WIN,
                "Hệ thống hoàn tiền do seller không hoàn tất bảo hành trong thời hạn"
        );
    }

    private DisputeResponse resolveLocked(
            OrderDispute dispute,
            Long resolverId,
            DisputeResolvedBy resolvedBy,
            DisputeResolution decision,
            String resolutionNote
    ) {

        HoldRelease holdRelease =
                holdReleaseRepository
                        .findByOrderItemId(
                                dispute.getOrderItemId()
                        )
                        .orElseThrow(
                                () -> new AppException(
                                        ErrorCode.HOLD_RELEASE_NOT_FOUND
                                )
                        );

        Long sellerId =
                disputeRepository
                        .findShopOwnerId(
                                dispute.getShopId()
                        )
                        .orElseThrow(
                                () -> new AppException(
                                        ErrorCode.SHOP_NOT_FOUND
                                )
                        );

        boolean buyerWin = decision == DisputeResolution.BUYER_WIN;

        /*
         * HoldReleaseService chịu trách nhiệm:
         *
         * BUYER:
         * - cancel seller hold
         * - refund buyer
         * - fee CANCELLED
         * - REFUNDED
         *
         * SELLER:
         * - HOLDING
         * - resume T+7
         */
        holdReleaseService.resolveDispute(
                holdRelease.getId(),
                buyerWin,
                dispute.getUserId(),
                sellerId,
                dispute.getOrderId()
        );

        dispute.setResolverId(resolverId);
        dispute.setResolvedBy(resolvedBy);

        dispute.setResolutionNote(
                resolutionNote
        );

        dispute.setResolvedAt(
                OffsetDateTime.now()
        );

        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolution(decision);

        if (buyerWin) {

            dispute.setRefundAmount(
                    holdRelease.getHoldAmount()
            );

        } else {

            dispute.setRefundAmount(
                    BigDecimal.ZERO
            );
        }

        disputeRepository.save(dispute);

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // ADMIN DETAIL
    // =========================================================

    @Transactional(readOnly = true)
    public DisputeResponse getById(
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

        return disputeMapper.toResponse(dispute);
    }

    // =========================================================
    // ADMIN LIST
    // =========================================================

    @Transactional(readOnly = true)
    public Page<DisputeResponse> getAll(
            String scope,
            String status,
            String keyword,
            boolean overdueOnly,
            Pageable pageable
    ) {

        String normalizedScope = scope == null || scope.isBlank()
                ? "QUEUE"
                : scope.trim().toUpperCase();
        if (!normalizedScope.equals("QUEUE") && !normalizedScope.equals("ALL")) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        DisputeStatus normalizedStatus = normalizedScope.equals("QUEUE")
                ? DisputeStatus.ADMIN_REVIEW
                : normalizeStatus(status);
        String normalizedKeyword = keyword == null || keyword.isBlank()
                ? ""
                : keyword.trim();

        return disputeRepository
                .findAdminDisputes(
                        normalizedStatus,
                        normalizedKeyword,
                        overdueOnly,
                        DisputeStatus.ADMIN_REVIEW,
                        OffsetDateTime.now(),
                        limitPageable(pageable)
                )
                .map(disputeMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminDisputeSummaryResponse getSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        return new AdminDisputeSummaryResponse(
                disputeRepository.countByStatus(DisputeStatus.ADMIN_REVIEW),
                disputeRepository.countByStatusAndDeadlineAtLessThanEqual(
                        DisputeStatus.ADMIN_REVIEW,
                        now
                )
        );
    }

    private DisputeStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DisputeStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
    }

    private Pageable limitPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), 100);
        return PageRequest.of(page, size);
    }
}
