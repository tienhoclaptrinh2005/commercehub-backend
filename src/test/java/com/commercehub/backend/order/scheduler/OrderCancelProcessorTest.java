package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.order.service.OrderStatusService;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.wallet.service.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class OrderCancelProcessorTest {

    @Test
    void expiredOrderUsesCanonicalOrderReferenceTypeWhenRefunding() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PreOrderItemRepository preOrderItemRepository = mock(PreOrderItemRepository.class);
        WalletService walletService = mock(WalletService.class);
        OrderStatusService orderStatusService = mock(OrderStatusService.class);
        OrderCancelProcessor processor = new OrderCancelProcessor(
                orderRepository,
                orderItemRepository,
                preOrderItemRepository,
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
                .status("WAITING_APPROVAL")
                .paymentStatus("PAID")
                .totalAmount(new BigDecimal("100000.00"))
                .approvalDeadlineAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        String reason = "Hệ thống tự động hủy và hoàn tiền do đơn quá hạn ở trạng thái WAITING_APPROVAL";

        when(orderRepository.findByIdWithLock(40L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of());

        processor.cancelSingleOrder(40L, reason);

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
                "WAITING_APPROVAL",
                "CANCELLED_BY_SYSTEM",
                null,
                reason
        );
    }
}
