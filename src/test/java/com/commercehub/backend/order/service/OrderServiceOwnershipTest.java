package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.mapper.OrderMapper;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.OrderStatusLogRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderServiceOwnershipTest {

    private OrderRepository orderRepository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        service = new OrderService(
                orderRepository,
                mock(OrderItemRepository.class),
                mock(PreOrderItemRepository.class),
                mock(OrderStatusLogRepository.class),
                mock(HoldReleaseRepository.class),
                mock(OrderDisputeRepository.class),
                mock(OrderStatusService.class),
                mock(OrderMapper.class)
        );
    }

    @Test
    void buyerOrderLookupCombinesOrderCodeAndCurrentBuyer() {
        Order order = Order.builder().id(41L).orderCode("ORD-S1-TEST-5414").build();
        when(orderRepository.findByOrderCodeAndUserId("ORD-S1-TEST-5414", 7L))
                .thenReturn(Optional.of(order));

        assertThat(service.getBuyerOrderOrThrow(7L, "ORD-S1-TEST-5414"))
                .isSameAs(order);

        verify(orderRepository).findByOrderCodeAndUserId("ORD-S1-TEST-5414", 7L);
        verify(orderRepository, never()).findById(anyLong());
    }

    @Test
    void buyerCannotResolveAnotherBuyersOrderCode() {
        when(orderRepository.findByOrderCodeAndUserId("ORD-S1-OTHER", 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBuyerOrderOrThrow(7L, "ORD-S1-OTHER"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECORD_NOT_FOUND));
    }

    @Test
    void checkoutResponseCannotContainOrderOutsideCurrentBuyer() {
        Order owned = Order.builder().id(41L).orderCode("ORD-S1-OWNED").build();
        when(orderRepository.findCheckoutOrdersForBuyer(7L, List.of(41L, 42L)))
                .thenReturn(List.of(owned));

        assertThatThrownBy(() -> service.getBuyerCheckoutOrders(7L, List.of(41L, 42L)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECORD_NOT_FOUND));
    }

    @Test
    void sellerOrderLookupCombinesOrderIdAndCurrentShop() {
        when(orderRepository.findByIdAndShopId(41L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSellerOrderOrThrow(9L, 41L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECORD_NOT_FOUND));

        verify(orderRepository).findByIdAndShopId(41L, 9L);
        verify(orderRepository, never()).findById(anyLong());
    }
}
