package com.commercehub.backend.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SellerDashboardResponse(
        String month,
        int daysInMonth,
        long orderCount,
        BigDecimal revenue,
        BigDecimal availableBalance,
        BigDecimal holdBalance,
        long newPreOrderRequestCount,
        long processingPreOrderCount,
        List<DailyRevenue> dailyRevenue,
        List<OrderStatusCount> orderStatusCounts,
        List<RecentOrder> recentOrders
) {
    public record DailyRevenue(int day, BigDecimal amount) {
    }

    public record OrderStatusCount(String status, long count) {
    }

    public record RecentOrder(
            Long orderId,
            String orderCode,
            String productSummary,
            BigDecimal totalAmount,
            String status,
            OffsetDateTime placedAt
    ) {
    }
}
