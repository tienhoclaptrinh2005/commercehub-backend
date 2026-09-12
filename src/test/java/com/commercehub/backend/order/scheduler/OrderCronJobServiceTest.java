package com.commercehub.backend.order.scheduler;

import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderCronJobServiceTest {

    @Test
    void doesNotRetrySameFailedOrderTwentyTimesInOneRun() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderCancelProcessor orderCancelProcessor = mock(OrderCancelProcessor.class);
        OrderCronJobService service = new OrderCronJobService(orderRepository, orderCancelProcessor);
        ReflectionTestUtils.setField(service, "batchSize", 200);

        when(orderRepository.findExpiredApprovalIds(
                eq(OrderStatus.WAITING_SELLER_ACCEPTANCE),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(40L));
        doThrow(new IllegalStateException("database rejected wallet transaction"))
                .when(orderCancelProcessor)
                .cancelSingleOrder(eq(40L), any(String.class));

        service.autoCancelExpiredWaitingApproval();

        verify(orderCancelProcessor, times(1))
                .cancelSingleOrder(eq(40L), any(String.class));
        verify(orderRepository, times(2)).findExpiredApprovalIds(
                eq(OrderStatus.WAITING_SELLER_ACCEPTANCE),
                any(OffsetDateTime.class),
                any(Pageable.class)
        );
    }
}
