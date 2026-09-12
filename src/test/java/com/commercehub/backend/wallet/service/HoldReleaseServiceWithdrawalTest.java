package com.commercehub.backend.wallet.service;

import com.commercehub.backend.fee.service.PlatformFeeLedgerService;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.entity.OrderPaymentStatus;
import com.commercehub.backend.order.entity.OrderStatus;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.entity.HoldReleaseStatus;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HoldReleaseServiceWithdrawalTest {

    @ParameterizedTest
    @EnumSource(value = HoldReleaseStatus.class, names = "FROZEN")
    void withdrawRestoresHoldingWithExactlyTheRemainingDuration(HoldReleaseStatus initialStatus) {
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

        assertThat(holdRelease.getStatus()).isEqualTo(HoldReleaseStatus.HOLDING);
        assertThat(holdRelease.getRemainingHoldSeconds()).isNull();
        assertThat(holdRelease.getScheduledReleaseAt())
                .isBetween(earliestRelease, latestRelease);
        verify(repository).save(holdRelease);
    }

    @Test
    void buyerWinChangesPaymentButKeepsDeliveredOrderLifecycle() {
        HoldReleaseRepository holdRepository = mock(HoldReleaseRepository.class);
        OrderItemRepository itemRepository = mock(OrderItemRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        WalletService walletService = mock(WalletService.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        HoldReleaseService service = new HoldReleaseService(
                holdRepository,
                mock(HoldReleaseProcessor.class),
                mock(PlatformFeeLedgerService.class),
                walletService,
                itemRepository,
                orderRepository,
                productRepository
        );

        Order order = Order.builder()
                .id(20L)
                .status(OrderStatus.DELIVERED)
                .paymentStatus(OrderPaymentStatus.PAID)
                .build();
        OrderItem item = OrderItem.builder()
                .id(30L)
                .order(order)
                .refundStatus("NONE")
                .build();
        HoldRelease holdRelease = HoldRelease.builder()
                .id(10L)
                .orderId(20L)
                .orderItemId(30L)
                .holdAmount(new BigDecimal("100.00"))
                .feeAmount(new BigDecimal("4.00"))
                .sellerNetAmount(new BigDecimal("96.00"))
                .status(HoldReleaseStatus.FROZEN)
                .remainingHoldSeconds(120L)
                .scheduledReleaseAt(OffsetDateTime.now().plusSeconds(120))
                .build();

        when(holdRepository.findByIdWithLock(10L)).thenReturn(Optional.of(holdRelease));
        when(itemRepository.findByIdWithLock(30L)).thenReturn(Optional.of(item));
        when(orderRepository.findByIdWithLock(20L)).thenReturn(Optional.of(order));
        when(itemRepository.countByOrderId(20L)).thenReturn(2L);
        when(itemRepository.countByOrderIdAndRefundStatus(20L, "REFUNDED")).thenReturn(1L);

        service.resolveDispute(10L, true, 40L, 50L, 20L);

        assertThat(holdRelease.getStatus()).isEqualTo(HoldReleaseStatus.REFUNDED);
        assertThat(item.getRefundStatus()).isEqualTo("REFUNDED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PARTIALLY_REFUNDED);
        verify(walletService).systemCreditBalance(40L, new BigDecimal("100.00"), "DISPUTE_REFUND", 10L, "HOLD_RELEASE");
    }
}
