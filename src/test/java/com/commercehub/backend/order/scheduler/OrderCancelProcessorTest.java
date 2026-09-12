package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderCancellationCode;
import com.commercehub.backend.order.entity.OrderCancelledBy;
import com.commercehub.backend.order.entity.OrderPaymentStatus;
import com.commercehub.backend.order.entity.OrderStatus;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.service.OrderStatusService;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.wallet.service.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderCancelProcessorTest {

    @Test
    void expiredOrderUsesCanonicalOrderReferenceTypeWhenRefunding() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        WalletService walletService = mock(WalletService.class);
        OrderStatusService orderStatusService = mock(OrderStatusService.class);
        OrderCancelProcessor processor = new OrderCancelProcessor(
                orderRepository,
                walletService,
                orderStatusService
        );

        User buyer = User.builder().id(1L).build();
        User seller = User.builder().id(2L).build();
        Shop shop = Shop.builder().id(3L).owner(seller).build();
        Order order = Order.builder()
                .id(40L)
                .user(buyer)
                .shop(shop)
                .status(OrderStatus.WAITING_SELLER_ACCEPTANCE)
                .paymentStatus(OrderPaymentStatus.PAID)
                .totalAmount(new BigDecimal("100000.00"))
                .approvalDeadlineAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        String reason = "Hệ thống tự động hủy và hoàn tiền do đơn quá hạn chờ người bán nhận";

        when(orderRepository.findByIdWithLock(40L)).thenReturn(Optional.of(order));
        processor.cancelSingleOrder(40L, reason);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
        assertThat(order.getCancelledBy()).isEqualTo(OrderCancelledBy.SYSTEM);
        assertThat(order.getCancellationCode()).isEqualTo(OrderCancellationCode.SELLER_ACCEPTANCE_TIMEOUT);
        assertThat(order.getCancellationReason()).isEqualTo(reason);
        assertThat(order.getCancelledAt()).isNotNull();

        verify(walletService).systemCancelSellerHold(2L, new BigDecimal("100000.00"), 40L);
        verify(walletService).systemCreditBalance(
                1L,
                new BigDecimal("100000.00"),
                "ORDER_REFUND",
                40L,
                "ORDER"
        );
        verify(orderStatusService).logStatusChange(
                order,
                OrderStatus.WAITING_SELLER_ACCEPTANCE,
                OrderStatus.CANCELLED,
                null,
                reason
        );
    }
}
