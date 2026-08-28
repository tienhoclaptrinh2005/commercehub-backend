package com.commercehub.backend.wallet.service;

import com.commercehub.backend.fee.service.PlatformFeeLedgerService;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HoldReleaseServiceWithdrawalTest {

    @ParameterizedTest
    @ValueSource(strings = {"COMPLAINED", "WARRANTY_IN_PROGRESS"})
    void withdrawRestoresHoldingWithExactlyTheRemainingDuration(String initialStatus) {
        HoldReleaseRepository repository = mock(HoldReleaseRepository.class);
        HoldReleaseService service = new HoldReleaseService(
                repository,
                mock(HoldReleaseProcessor.class),
                mock(PlatformFeeLedgerService.class),
                mock(WalletService.class),
                mock(OrderItemRepository.class),
                mock(OrderRepository.class),
                mock(ProductRepository.class)
        );
        HoldRelease holdRelease = HoldRelease.builder()
                .id(10L)
                .orderItemId(30L)
                .status(initialStatus)
                .remainingHoldSeconds(120L)
                .scheduledReleaseAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(repository.findByOrderItemIdWithLock(30L)).thenReturn(Optional.of(holdRelease));

        OffsetDateTime earliestRelease = OffsetDateTime.now().plusSeconds(120);
        service.withdrawComplaint(30L);
        OffsetDateTime latestRelease = OffsetDateTime.now().plusSeconds(120);

        assertThat(holdRelease.getStatus()).isEqualTo("HOLDING");
        assertThat(holdRelease.getRemainingHoldSeconds()).isNull();
        assertThat(holdRelease.getScheduledReleaseAt())
                .isBetween(earliestRelease, latestRelease);
        verify(repository).save(holdRelease);
    }
}
