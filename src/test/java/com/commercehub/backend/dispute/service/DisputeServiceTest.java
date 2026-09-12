package com.commercehub.backend.dispute.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dispute.dto.request.CreateDisputeRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.entity.OrderDispute;
import com.commercehub.backend.dispute.entity.DisputeResolution;
import com.commercehub.backend.dispute.entity.DisputeResolvedBy;
import com.commercehub.backend.dispute.entity.DisputeStatus;
import com.commercehub.backend.dispute.mapper.DisputeMapper;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.wallet.service.HoldReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DisputeServiceTest {

    private OrderDisputeRepository disputeRepository;
    private DisputeMapper disputeMapper;
    private HoldReleaseService holdReleaseService;
    private DisputeService service;

    @BeforeEach
    void setUp() {
        disputeRepository = mock(OrderDisputeRepository.class);
        disputeMapper = mock(DisputeMapper.class);
        holdReleaseService = mock(HoldReleaseService.class);
        service = new DisputeService(disputeRepository, disputeMapper, holdReleaseService);
        ReflectionTestUtils.setField(service, "warrantyProcessingHours", 24L);
    }

    @Test
    void buyerCanWithdrawOpenComplaintAndItIsClosedPermanently() {
        OrderDispute dispute = dispute(DisputeStatus.OPEN);
        DisputeResponse response = mock(DisputeResponse.class);
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));
        when(disputeMapper.toResponse(dispute)).thenReturn(response);

        DisputeResponse result = service.withdrawByBuyer(40L, 10L);

        assertThat(result).isSameAs(response);
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(dispute.getResolution()).isEqualTo(DisputeResolution.BUYER_WITHDREW);
        assertThat(dispute.getResolvedBy()).isEqualTo(DisputeResolvedBy.BUYER);
        assertThat(dispute.getResolvedAt()).isNotNull();
        verify(holdReleaseService).withdrawComplaint(30L);
        verify(disputeRepository).save(dispute);
    }

    @Test
    void buyerCannotWithdrawAnotherBuyersComplaint() {
        OrderDispute dispute = dispute(DisputeStatus.OPEN);
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.withdrawByBuyer(41L, 10L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DISPUTE_ACCESS_DENIED));

        verifyNoInteractions(holdReleaseService);
    }

    @Test
    void buyerCannotWithdrawWaitingOrTerminalComplaint() {
        OrderDispute dispute = dispute(DisputeStatus.WAITING_BUYER_CONFIRMATION);
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.withdrawByBuyer(40L, 10L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DISPUTE_WITHDRAW_NOT_ALLOWED));

        verifyNoInteractions(holdReleaseService);
    }

    @Test
    void closedComplaintStillPreventsASecondComplaintForTheSameItem() {
        when(disputeRepository.buyerOwnsOrderItem(40L, 20L, 30L)).thenReturn(true);
        when(disputeRepository.existsByOrderItemId(30L)).thenReturn(true);

        assertThatThrownBy(() -> service.createComplaint(
                40L,
                20L,
                30L,
                new CreateDisputeRequest("Lỗi", List.of())
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DISPUTE_ALREADY_EXISTS));

        verifyNoInteractions(holdReleaseService);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void buyerCannotComplainAboutAnotherBuyersOrderItem() {
        when(disputeRepository.buyerOwnsOrderItem(41L, 20L, 30L)).thenReturn(false);

        assertThatThrownBy(() -> service.createComplaint(
                41L,
                20L,
                30L,
                new CreateDisputeRequest("Lỗi", List.of())
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));

        verify(disputeRepository, never()).save(any());
        verifyNoInteractions(holdReleaseService);
    }

    @Test
    void buyerCanWithdrawWarrantyBeforeDeadline() {
        OrderDispute dispute = dispute(DisputeStatus.WARRANTY_IN_PROGRESS);
        dispute.setDeadlineAt(OffsetDateTime.now().plusMinutes(1));
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));

        service.withdrawByBuyer(40L, 10L);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(dispute.getResolution()).isEqualTo(DisputeResolution.BUYER_WITHDREW);
        verify(holdReleaseService).withdrawComplaint(30L);
    }

    @Test
    void sellerStartingWarrantyGetsFreshTwentyFourHourDeadline() {
        OrderDispute dispute = dispute(DisputeStatus.OPEN);
        when(disputeRepository.sellerOwnsOrderItem(70L, 20L, 30L)).thenReturn(true);
        when(disputeRepository.findByOrderItemIdWithLock(30L)).thenReturn(Optional.of(dispute));

        OffsetDateTime earliestDeadline = OffsetDateTime.now().plusHours(24);
        service.startWarranty(70L, 20L, 30L, null);
        OffsetDateTime latestDeadline = OffsetDateTime.now().plusHours(24);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.WARRANTY_IN_PROGRESS);
        assertThat(dispute.getDeadlineAt()).isBetween(earliestDeadline, latestDeadline);
        verify(holdReleaseService).startWarranty(30L);
        verify(disputeRepository).save(dispute);
    }

    @Test
    void sellerCannotCompleteWarrantyAfterDeadlineBeforeSchedulerRuns() {
        OrderDispute dispute = dispute(DisputeStatus.WARRANTY_IN_PROGRESS);
        dispute.setDeadlineAt(OffsetDateTime.now().minusSeconds(1));
        when(disputeRepository.sellerOwnsOrderItem(70L, 20L, 30L)).thenReturn(true);
        when(disputeRepository.findByOrderItemIdWithLock(30L)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.completeWarranty(70L, 20L, 30L, null))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DISPUTE_WARRANTY_DEADLINE_EXPIRED));

        verifyNoInteractions(holdReleaseService);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void buyerCannotWithdrawExpiredWarrantyBeforeSchedulerRuns() {
        OrderDispute dispute = dispute(DisputeStatus.WARRANTY_IN_PROGRESS);
        dispute.setDeadlineAt(OffsetDateTime.now().minusSeconds(1));
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.withdrawByBuyer(40L, 10L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DISPUTE_WARRANTY_DEADLINE_EXPIRED));

        verifyNoInteractions(holdReleaseService);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void expiredOpenCannotBeWithdrawnStartedOrEscalatedBeforeSchedulerRuns() {
        OrderDispute dispute = dispute(DisputeStatus.OPEN);
        dispute.setDeadlineAt(OffsetDateTime.now().minusSeconds(1));
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.sellerOwnsOrderItem(70L, 20L, 30L)).thenReturn(true);
        when(disputeRepository.findByOrderItemIdWithLock(30L)).thenReturn(Optional.of(dispute));

        assertDeadlineError(
                () -> service.withdrawByBuyer(40L, 10L),
                ErrorCode.DISPUTE_SELLER_RESPONSE_DEADLINE_EXPIRED
        );
        assertDeadlineError(
                () -> service.startWarranty(70L, 20L, 30L, null),
                ErrorCode.DISPUTE_SELLER_RESPONSE_DEADLINE_EXPIRED
        );
        assertDeadlineError(
                () -> service.escalateBySeller(70L, 20L, 30L, null),
                ErrorCode.DISPUTE_SELLER_RESPONSE_DEADLINE_EXPIRED
        );

        verifyNoInteractions(holdReleaseService);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void expiredWarrantyCannotBeEscalatedBeforeSchedulerRuns() {
        OrderDispute dispute = dispute(DisputeStatus.WARRANTY_IN_PROGRESS);
        dispute.setDeadlineAt(OffsetDateTime.now().minusSeconds(1));
        when(disputeRepository.sellerOwnsOrderItem(70L, 20L, 30L)).thenReturn(true);
        when(disputeRepository.findByOrderItemIdWithLock(30L)).thenReturn(Optional.of(dispute));

        assertDeadlineError(
                () -> service.escalateBySeller(70L, 20L, 30L, null),
                ErrorCode.DISPUTE_WARRANTY_DEADLINE_EXPIRED
        );

        verifyNoInteractions(holdReleaseService);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void expiredBuyerConfirmationCannotBeConfirmedOrEscalatedBeforeSchedulerRuns() {
        OrderDispute dispute = dispute(DisputeStatus.WAITING_BUYER_CONFIRMATION);
        dispute.setDeadlineAt(OffsetDateTime.now().minusSeconds(1));
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));

        assertDeadlineError(
                () -> service.confirmWarrantyByBuyer(40L, 10L),
                ErrorCode.DISPUTE_BUYER_CONFIRMATION_DEADLINE_EXPIRED
        );
        assertDeadlineError(
                () -> service.escalateByBuyer(40L, 10L),
                ErrorCode.DISPUTE_BUYER_CONFIRMATION_DEADLINE_EXPIRED
        );

        verifyNoInteractions(holdReleaseService);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void buyerConfirmationStoresAcceptedWarrantyReason() {
        OrderDispute dispute = dispute(DisputeStatus.WAITING_BUYER_CONFIRMATION);
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));

        service.confirmWarrantyByBuyer(40L, 10L);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(dispute.getResolution()).isEqualTo(DisputeResolution.WARRANTY_ACCEPTED);
        assertThat(dispute.getResolvedBy()).isEqualTo(DisputeResolvedBy.BUYER);
        verify(holdReleaseService).confirmWarrantyResolved(30L);
    }

    @Test
    void expiredBuyerConfirmationStoresTimeoutReason() {
        OffsetDateTime now = OffsetDateTime.now();
        OrderDispute dispute = dispute(DisputeStatus.WAITING_BUYER_CONFIRMATION);
        dispute.setDeadlineAt(now.minusSeconds(1));
        when(disputeRepository.findByIdWithLock(10L)).thenReturn(Optional.of(dispute));

        service.closeExpiredBuyerConfirmation(10L, now);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(dispute.getResolution()).isEqualTo(DisputeResolution.BUYER_CONFIRMATION_TIMEOUT);
        assertThat(dispute.getResolvedBy()).isEqualTo(DisputeResolvedBy.SYSTEM);
        assertThat(dispute.getResolvedAt()).isEqualTo(now);
        verify(holdReleaseService).confirmWarrantyResolved(30L);
    }

    private OrderDispute dispute(DisputeStatus status) {
        return OrderDispute.builder()
                .id(10L)
                .orderId(20L)
                .orderItemId(30L)
                .userId(40L)
                .shopId(50L)
                .status(status)
                .deadlineAt(OffsetDateTime.now().plusHours(1))
                .build();
    }

    private void assertDeadlineError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            ErrorCode expectedError
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expectedError));
    }
}
