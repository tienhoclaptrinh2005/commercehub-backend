package com.commercehub.backend.dispute.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dispute.dto.request.AdminResolveDisputeRequest;
import com.commercehub.backend.dispute.entity.DisputeResolution;
import com.commercehub.backend.dispute.entity.DisputeStatus;
import com.commercehub.backend.dispute.mapper.DisputeMapper;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import com.commercehub.backend.wallet.service.HoldReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisputeResolutionServiceTest {

    private OrderDisputeRepository disputeRepository;
    private DisputeResolutionService service;

    @BeforeEach
    void setUp() {
        disputeRepository = mock(OrderDisputeRepository.class);
        service = new DisputeResolutionService(
                disputeRepository,
                mock(HoldReleaseRepository.class),
                mock(HoldReleaseService.class),
                mock(DisputeMapper.class)
        );
    }

    @Test
    void defaultAdminListOnlyQueriesAdminReviewQueue() {
        when(disputeRepository.findAdminDisputes(
                eq(DisputeStatus.ADMIN_REVIEW),
                eq(""),
                eq(false),
                eq(DisputeStatus.ADMIN_REVIEW),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.getAll(null, null, null, false, PageRequest.of(0, 20));

        verify(disputeRepository).findAdminDisputes(
                eq(DisputeStatus.ADMIN_REVIEW),
                eq(""),
                eq(false),
                eq(DisputeStatus.ADMIN_REVIEW),
                any(OffsetDateTime.class),
                any(Pageable.class)
        );
    }

    @Test
    void adminSummaryCountsOnlyReviewAndOverdueReview() {
        when(disputeRepository.countByStatus(DisputeStatus.ADMIN_REVIEW)).thenReturn(7L);
        when(disputeRepository.countByStatusAndDeadlineAtLessThanEqual(
                eq(DisputeStatus.ADMIN_REVIEW),
                any(OffsetDateTime.class)
        )).thenReturn(2L);

        var summary = service.getSummary();

        assertThat(summary.pendingCount()).isEqualTo(7);
        assertThat(summary.overdueCount()).isEqualTo(2);
    }

    @Test
    void adminCannotResolveWithoutNoteEvenWhenCallingServiceDirectly() {
        var request = new AdminResolveDisputeRequest(DisputeResolution.BUYER_WIN, "   ");

        assertThatThrownBy(() -> service.resolve(1L, 2L, request))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verifyNoInteractions(disputeRepository);
    }
}
