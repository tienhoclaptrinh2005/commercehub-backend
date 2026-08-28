package com.commercehub.backend.dispute.scheduler;

import com.commercehub.backend.dispute.entity.OrderDispute;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.dispute.service.DisputeResolutionService;
import com.commercehub.backend.dispute.service.DisputeService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisputeDeadlineSchedulerTest {

    @Test
    void schedulerDispatchesExpiredWarrantyToAutomaticBuyerRefund() {
        OrderDisputeRepository repository = mock(OrderDisputeRepository.class);
        DisputeResolutionService resolutionService = mock(DisputeResolutionService.class);
        DisputeService disputeService = mock(DisputeService.class);
        DisputeDeadlineScheduler scheduler = new DisputeDeadlineScheduler(
                repository,
                resolutionService,
                disputeService
        );
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);

        when(repository.findExpiredIds(
                eq(OrderDispute.STATUS_WARRANTY_IN_PROGRESS),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(10L)).thenReturn(List.of());

        scheduler.processDeadlines();

        verify(resolutionService).resolveExpiredWarrantyForBuyer(
                eq(10L),
                any(OffsetDateTime.class)
        );
    }
}
