package com.commercehub.backend.fee.service;

import com.commercehub.backend.fee.dto.response.FeeLedgerResponse;
import com.commercehub.backend.fee.entity.PlatformFeeLedger;
import com.commercehub.backend.fee.mapper.FeeMapper;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
import com.commercehub.backend.fee.repository.PlatformFeeLogRepository;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformFeeLedgerServiceTest {

    @Test
    void sellerFeeHistoryContainsPublicOrderAndPurchasedItemContext() {
        PlatformFeeLedgerRepository ledgerRepository = mock(PlatformFeeLedgerRepository.class);
        PlatformFeeLogRepository logRepository = mock(PlatformFeeLogRepository.class);
        ShopFeeSummaryService summaryService = mock(ShopFeeSummaryService.class);
        FeeMapper feeMapper = mock(FeeMapper.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PlatformFeeLedgerService service = new PlatformFeeLedgerService(
                ledgerRepository,
                logRepository,
                summaryService,
                feeMapper,
                orderRepository,
                orderItemRepository
        );

        PlatformFeeLedger ledger = PlatformFeeLedger.builder()
                .id(1L)
                .orderId(41L)
                .orderItemId(71L)
                .shopId(9L)
                .build();
        Order order = Order.builder().id(41L).orderCode("ORD-S1-202609130001").build();
        OrderItem item = OrderItem.builder()
                .id(71L)
                .order(order)
                .productName("Microsoft 365")
                .variantName("12 tháng")
                .build();
        PageRequest pageable = PageRequest.of(0, 10);

        when(ledgerRepository.findByShopIdOrderByCreatedAtDesc(9L, pageable))
                .thenReturn(new PageImpl<>(List.of(ledger), pageable, 1));
        when(orderRepository.findAllById(Set.of(41L))).thenReturn(List.of(order));
        when(orderItemRepository.findAllById(Set.of(71L))).thenReturn(List.of(item));
        when(feeMapper.toFeeLedgerResponse(ledger)).thenReturn(
                FeeLedgerResponse.builder().id(1L).orderId(41L).orderItemId(71L).build()
        );

        FeeLedgerResponse response = service.getMyShopFees(9L, pageable).getContent().get(0);

        assertThat(response.getOrderCode()).isEqualTo("ORD-S1-202609130001");
        assertThat(response.getProductName()).isEqualTo("Microsoft 365");
        assertThat(response.getVariantName()).isEqualTo("12 tháng");
    }
}
