package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.order.dto.response.OrderResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.mapper.OrderMapper;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.OrderStatusLogRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceSellerListTest {

    private OrderRepository orderRepository;
    private OrderDisputeRepository disputeRepository;
    private OrderItemRepository orderItemRepository;
    private OrderMapper orderMapper;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        disputeRepository = mock(OrderDisputeRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        orderMapper = mock(OrderMapper.class);
        service = new OrderService(
                orderRepository,
                orderItemRepository,
                mock(PreOrderItemRepository.class),
                mock(OrderStatusLogRepository.class),
                mock(HoldReleaseRepository.class),
                disputeRepository,
                mock(OrderStatusService.class),
                orderMapper
        );
    }

    @Test
    void sellerOrdersNormalizeFiltersAndUseSliceWithoutCount() {
        Order order = Order.builder().id(41L).placedAt(OffsetDateTime.parse("2026-09-10T10:00:00+07:00")).build();
        OrderResponse response = OrderResponse.builder().id(41L).status("PROCESSING").build();
        when(orderRepository.findFirstSellerOrders(
                eq(9L), eq("ORD-S1"), eq("PRE_ORDER"), eq("PROCESSING"),
                any(OffsetDateTime.class), any(OffsetDateTime.class), anySet(), eq(PageRequest.of(0, 10))
        )).thenReturn(new SliceImpl<>(List.of(order), PageRequest.of(0, 10), false));
        when(disputeRepository.findOrderIdsWithStatuses(eq(List.of(41L)), anySet())).thenReturn(Set.of());
        when(orderItemRepository.findByOrderIdIn(List.of(41L))).thenReturn(List.of(
                OrderItem.builder()
                        .id(71L)
                        .order(order)
                        .productName("Microsoft 365")
                        .variantName("12 tháng")
                        .build()
        ));
        when(orderMapper.toOrderResponse(order)).thenReturn(response);

        var result = service.getSellerOrders(
                9L,
                "  ORD-S1  ",
                "pre_order",
                "processing",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                null,
                null,
                10
        );

        assertThat(result.getContent()).containsExactly(response);
        assertThat(response.getProductNames()).containsExactly("Microsoft 365");
        assertThat(response.getVariantNames()).containsExactly("12 tháng");
        verify(orderRepository).findFirstSellerOrders(
                eq(9L), eq("ORD-S1"), eq("PRE_ORDER"), eq("PROCESSING"),
                eq(OffsetDateTime.parse("2026-09-01T00:00:00+07:00")),
                eq(OffsetDateTime.parse("2026-10-01T00:00:00+07:00")),
                anySet(), eq(PageRequest.of(0, 10))
        );
        verify(orderRepository, never()).count();
    }

    @Test
    void sellerOrdersRejectUnsupportedDeliveryType() {
        assertThatThrownBy(() -> service.getSellerOrders(
                9L, null, "DELIVERY", null, null, null, null, null, 10
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DELIVERY_TYPE));
    }

    @Test
    void sellerOrdersRequireCompleteCursorPair() {
        assertThatThrownBy(() -> service.getSellerOrders(
                9L, null, null, null, null, null,
                OffsetDateTime.parse("2026-09-10T10:00:00+07:00"), null, 10
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
