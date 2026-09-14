package com.commercehub.backend.dashboard.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dashboard.dto.response.SellerDashboardResponse;
import com.commercehub.backend.dashboard.dto.response.SellerNotificationResponse;
import com.commercehub.backend.dashboard.repository.SellerDashboardRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.service.ShopService;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class SellerDashboardServiceTest {

    private ShopService shopService;
    private WalletRepository walletRepository;
    private SellerDashboardRepository dashboardRepository;
    private SellerDashboardService service;

    @BeforeEach
    void setUp() {
        shopService = mock(ShopService.class);
        walletRepository = mock(WalletRepository.class);
        dashboardRepository = mock(SellerDashboardRepository.class);
        service = new SellerDashboardService(
                shopService,
                walletRepository,
                dashboardRepository
        );
    }

    @Test
    void returnsEveryDayOfSelectedMonthAndUsesRealAggregates() {
        SellerDashboardRepository.DailyRevenueProjection firstDay = dailyRow(1, 2L, "150000");
        SellerDashboardRepository.DailyRevenueProjection thirdDay = dailyRow(3, 1L, "50000");
        SellerDashboardRepository.OrderStatusCountProjection status =
                mock(SellerDashboardRepository.OrderStatusCountProjection.class);
        SellerDashboardRepository.RecentOrderProjection recent =
                mock(SellerDashboardRepository.RecentOrderProjection.class);
        SellerDashboardRepository.PreOrderWorkloadProjection workload =
                mock(SellerDashboardRepository.PreOrderWorkloadProjection.class);

        when(shopService.getShopByOwnerId(9L)).thenReturn(Shop.builder().id(7L).build());
        when(walletRepository.findByUserId(9L)).thenReturn(Optional.of(Wallet.builder()
                .availableBalance(new BigDecimal("900000"))
                .holdBalance(new BigDecimal("200000"))
                .build()));
        when(dashboardRepository.findMonthlyRevenue(
                eq(7L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(List.of(firstDay, thirdDay));

        when(status.getStatus()).thenReturn("DELIVERED");
        when(status.getCount()).thenReturn(2L);
        when(dashboardRepository.findMonthlyOrderStatusCounts(
                eq(7L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(List.of(status));

        when(recent.getOrderId()).thenReturn(41L);
        when(recent.getOrderCode()).thenReturn("ORD-S1-20260904103025123-5414");
        when(recent.getProductName()).thenReturn("Netflix Family");
        when(recent.getItemCount()).thenReturn(2L);
        when(recent.getTotalAmount()).thenReturn(new BigDecimal("200000"));
        when(recent.getStatus()).thenReturn("DELIVERED");
        when(recent.getPlacedAt()).thenReturn(Instant.parse("2026-09-03T03:30:00Z"));
        when(dashboardRepository.findRecentOrders(7L)).thenReturn(List.of(recent));
        when(workload.getNewRequestCount()).thenReturn(3L);
        when(workload.getProcessingCount()).thenReturn(2L);
        when(dashboardRepository.findCurrentPreOrderWorkload(7L)).thenReturn(workload);

        SellerDashboardResponse response = service.getDashboard(9L, "2026-09");

        assertThat(response.month()).isEqualTo("2026-09");
        assertThat(response.daysInMonth()).isEqualTo(30);
        assertThat(response.dailyRevenue()).hasSize(30);
        assertThat(response.dailyRevenue().get(0).amount()).isEqualByComparingTo("150000");
        assertThat(response.dailyRevenue().get(1).amount()).isZero();
        assertThat(response.dailyRevenue().get(2).amount()).isEqualByComparingTo("50000");
        assertThat(response.orderCount()).isEqualTo(3L);
        assertThat(response.revenue()).isEqualByComparingTo("200000");
        assertThat(response.availableBalance()).isEqualByComparingTo("900000");
        assertThat(response.holdBalance()).isEqualByComparingTo("200000");
        assertThat(response.newPreOrderRequestCount()).isEqualTo(3L);
        assertThat(response.processingPreOrderCount()).isEqualTo(2L);
        assertThat(response.recentOrders().getFirst().productSummary())
                .isEqualTo("Netflix Family +1 mục");
        assertThat(response.recentOrders().getFirst().placedAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-03T10:30:00+07:00"));
    }

    @Test
    void rejectsInvalidOrFutureMonthBeforeQueryingData() {
        assertThatThrownBy(() -> service.getDashboard(9L, "2026-13"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> service.getDashboard(9L, "2099-01"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void notificationCountsUseRealCurrentSellerWorkload() {
        SellerDashboardRepository.SellerNotificationProjection counts =
                mock(SellerDashboardRepository.SellerNotificationProjection.class);
        when(shopService.getShopByOwnerId(9L)).thenReturn(Shop.builder().id(7L).build());
        when(counts.getRecentInstantOrderCount()).thenReturn(4L);
        when(counts.getNewPreOrderRequestCount()).thenReturn(12L);
        when(counts.getProcessingPreOrderCount()).thenReturn(3L);
        when(counts.getActiveDisputeCount()).thenReturn(2L);
        when(counts.getWithdrawalUpdateCount()).thenReturn(1L);
        when(dashboardRepository.findSellerNotificationCounts(eq(7L), eq(9L), any(OffsetDateTime.class)))
                .thenReturn(counts);

        SellerNotificationResponse response = service.getNotifications(9L);

        assertThat(response.recentInstantOrderCount()).isEqualTo(4L);
        assertThat(response.newPreOrderRequestCount()).isEqualTo(12L);
        assertThat(response.processingPreOrderCount()).isEqualTo(3L);
        assertThat(response.activeDisputeCount()).isEqualTo(2L);
        assertThat(response.withdrawalUpdateCount()).isEqualTo(1L);
    }

    @Test
    void markingOneNotificationCategoryPersistsReadTimeAndReturnsFreshCounts() {
        SellerDashboardRepository.SellerNotificationProjection counts =
                mock(SellerDashboardRepository.SellerNotificationProjection.class);
        when(shopService.getShopByOwnerId(9L)).thenReturn(Shop.builder().id(7L).build());
        when(dashboardRepository.findSellerNotificationCounts(eq(7L), eq(9L), any(OffsetDateTime.class)))
                .thenReturn(counts);

        SellerNotificationResponse response = service.markNotificationsRead(9L, "pre_orders");

        verify(dashboardRepository).markSellerNotificationCategoryRead(9L, "PRE_ORDERS");
        assertThat(response.newPreOrderRequestCount()).isZero();
    }

    @Test
    void markingWithdrawalNotificationsReadUsesDedicatedCategory() {
        SellerDashboardRepository.SellerNotificationProjection counts =
                mock(SellerDashboardRepository.SellerNotificationProjection.class);
        when(shopService.getShopByOwnerId(9L)).thenReturn(Shop.builder().id(7L).build());
        when(dashboardRepository.findSellerNotificationCounts(eq(7L), eq(9L), any(OffsetDateTime.class)))
                .thenReturn(counts);

        SellerNotificationResponse response = service.markNotificationsRead(9L, "withdrawals");

        verify(dashboardRepository).markSellerNotificationCategoryRead(9L, "WITHDRAWALS");
        assertThat(response.withdrawalUpdateCount()).isZero();
    }

    private SellerDashboardRepository.DailyRevenueProjection dailyRow(
            int day,
            long orderCount,
            String revenue
    ) {
        SellerDashboardRepository.DailyRevenueProjection row =
                mock(SellerDashboardRepository.DailyRevenueProjection.class);
        when(row.getDay()).thenReturn(day);
        when(row.getOrderCount()).thenReturn(orderCount);
        when(row.getRevenue()).thenReturn(new BigDecimal(revenue));
        return row;
    }
}
