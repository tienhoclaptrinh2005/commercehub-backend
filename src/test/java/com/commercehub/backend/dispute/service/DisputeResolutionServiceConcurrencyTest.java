package com.commercehub.backend.dispute.service;

import com.commercehub.backend.dispute.dto.request.AdminResolveDisputeRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.entity.OrderDispute;
import com.commercehub.backend.dispute.mapper.DisputeMapper;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import com.commercehub.backend.wallet.service.HoldReleaseService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DisputeResolutionServiceConcurrencyTest {

    @Test
    void concurrentExpiredWarrantyRefundsBuyerOnlyOnceWithoutAdminReview() throws Exception {
        OrderDisputeRepository disputeRepository = mock(OrderDisputeRepository.class);
        HoldReleaseRepository holdReleaseRepository = mock(HoldReleaseRepository.class);
        HoldReleaseService holdReleaseService = mock(HoldReleaseService.class);
        DisputeMapper mapper = mock(DisputeMapper.class);
        DisputeResolutionService service = new DisputeResolutionService(
                disputeRepository, holdReleaseRepository, holdReleaseService, mapper);

        OffsetDateTime now = OffsetDateTime.now();
        OrderDispute dispute = OrderDispute.builder()
                .id(10L)
                .orderId(20L)
                .orderItemId(30L)
                .userId(40L)
                .shopId(50L)
                .status(OrderDispute.STATUS_WARRANTY_IN_PROGRESS)
                .deadlineAt(now.minusSeconds(1))
                .build();
        HoldRelease holdRelease = HoldRelease.builder()
                .id(60L)
                .orderId(20L)
                .orderItemId(30L)
                .holdAmount(new BigDecimal("100.00"))
                .build();
        ReentrantLock simulatedDatabaseLock = new ReentrantLock();
        when(disputeRepository.findByIdWithLock(10L)).thenAnswer(invocation -> {
            simulatedDatabaseLock.lock();
            if (!OrderDispute.STATUS_WARRANTY_IN_PROGRESS.equals(dispute.getStatus())) {
                simulatedDatabaseLock.unlock();
            }
            return Optional.of(dispute);
        });
        when(holdReleaseRepository.findByOrderItemId(30L)).thenReturn(Optional.of(holdRelease));
        when(disputeRepository.findShopOwnerId(50L)).thenReturn(Optional.of(70L));
        when(disputeRepository.save(any())).thenAnswer(invocation -> {
            if (simulatedDatabaseLock.isHeldByCurrentThread()) {
                simulatedDatabaseLock.unlock();
            }
            return invocation.getArgument(0);
        });

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                service.resolveExpiredWarrantyForBuyer(10L, now);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                service.resolveExpiredWarrantyForBuyer(10L, now);
                return null;
            });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        verify(holdReleaseService, times(1)).escalateDispute(30L);
        verify(disputeRepository, times(1)).linkFeeLedgerToDispute(30L, 10L);
        verify(holdReleaseService, times(1)).resolveDispute(60L, true, 40L, 70L, 20L);
        assertThat(dispute.getStatus()).isEqualTo(OrderDispute.STATUS_BUYER_WIN);
        assertThat(dispute.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(dispute.getResolverId()).isNull();
        assertThat(dispute.getAdminNote()).contains("seller không hoàn tất bảo hành");
    }

    @Test
    void concurrentSameDecisionSettlesMoneyOnlyOnce() throws Exception {
        OrderDisputeRepository disputeRepository = mock(OrderDisputeRepository.class);
        HoldReleaseRepository holdReleaseRepository = mock(HoldReleaseRepository.class);
        HoldReleaseService holdReleaseService = mock(HoldReleaseService.class);
        DisputeMapper mapper = mock(DisputeMapper.class);
        DisputeResolutionService service = new DisputeResolutionService(
                disputeRepository, holdReleaseRepository, holdReleaseService, mapper);

        OrderDispute dispute = OrderDispute.builder()
                .id(10L)
                .orderId(20L)
                .orderItemId(30L)
                .userId(40L)
                .shopId(50L)
                .status(OrderDispute.STATUS_PROCESSING)
                .build();
        HoldRelease holdRelease = HoldRelease.builder()
                .id(60L)
                .orderId(20L)
                .orderItemId(30L)
                .holdAmount(new BigDecimal("100.00"))
                .feeAmount(new BigDecimal("4.00"))
                .sellerNetAmount(new BigDecimal("96.00"))
                .build();
        DisputeResponse response = mock(DisputeResponse.class);

        ReentrantLock simulatedDatabaseLock = new ReentrantLock();
        when(disputeRepository.findByIdWithLock(10L)).thenAnswer(invocation -> {
            simulatedDatabaseLock.lock();
            if (!OrderDispute.STATUS_PROCESSING.equals(dispute.getStatus())) {
                simulatedDatabaseLock.unlock();
            }
            return Optional.of(dispute);
        });
        when(holdReleaseRepository.findByOrderItemId(30L)).thenReturn(Optional.of(holdRelease));
        when(disputeRepository.findShopOwnerId(50L)).thenReturn(Optional.of(70L));
        when(disputeRepository.save(any())).thenAnswer(invocation -> {
            if (simulatedDatabaseLock.isHeldByCurrentThread()) {
                simulatedDatabaseLock.unlock();
            }
            return invocation.getArgument(0);
        });
        when(mapper.toResponse(dispute)).thenReturn(response);

        AdminResolveDisputeRequest request =
                new AdminResolveDisputeRequest(OrderDispute.STATUS_BUYER_WIN, "Buyer thắng");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return service.resolve(80L, 10L, request);
            });
            var second = executor.submit(() -> {
                start.await();
                return service.resolve(80L, 10L, request);
            });
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(response);
            assertThat(second.get(5, TimeUnit.SECONDS)).isSameAs(response);
        }

        verify(holdReleaseService, times(1)).resolveDispute(
                60L, true, 40L, 70L, 20L);
        verify(disputeRepository, times(1)).save(dispute);
    }
}
