package com.commercehub.backend.dashboard.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.dashboard.dto.response.SellerDashboardResponse;
import com.commercehub.backend.dashboard.repository.SellerDashboardRepository;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.service.ShopService;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SellerDashboardService {

    private static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private static final YearMonth EARLIEST_ALLOWED_MONTH = YearMonth.of(2000, 1);

    private final ShopService shopService;
    private final WalletRepository walletRepository;
    private final ProductRepository productRepository;
    private final SellerDashboardRepository dashboardRepository;

    @Transactional(readOnly = true)
    public SellerDashboardResponse getDashboard(Long sellerId, String requestedMonth) {
        YearMonth month = parseMonth(requestedMonth);
        Shop shop = shopService.getShopByOwnerId(sellerId);
        Wallet wallet = walletRepository.findByUserId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        OffsetDateTime fromTime = month.atDay(1).atStartOfDay().atOffset(BUSINESS_OFFSET);
        OffsetDateTime toTime = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(BUSINESS_OFFSET);

        List<SellerDashboardRepository.DailyRevenueProjection> revenueRows =
                dashboardRepository.findMonthlyRevenue(shop.getId(), fromTime, toTime);
        Map<Integer, BigDecimal> revenueByDay = new HashMap<>();
        long orderCount = 0L;
        BigDecimal revenue = BigDecimal.ZERO;

        for (SellerDashboardRepository.DailyRevenueProjection row : revenueRows) {
            int day = row.getDay();
            BigDecimal dailyAmount = zeroIfNull(row.getRevenue());
            revenueByDay.put(day, dailyAmount);
            orderCount += row.getOrderCount() == null ? 0L : row.getOrderCount();
            revenue = revenue.add(dailyAmount);
        }

        List<SellerDashboardResponse.DailyRevenue> dailyRevenue =
                java.util.stream.IntStream.rangeClosed(1, month.lengthOfMonth())
                        .mapToObj(day -> new SellerDashboardResponse.DailyRevenue(
                                day,
                                revenueByDay.getOrDefault(day, BigDecimal.ZERO)
                        ))
                        .toList();

        List<SellerDashboardResponse.OrderStatusCount> statusCounts = dashboardRepository
                .findMonthlyOrderStatusCounts(shop.getId(), fromTime, toTime)
                .stream()
                .map(row -> new SellerDashboardResponse.OrderStatusCount(
                        row.getStatus(),
                        row.getCount() == null ? 0L : row.getCount()
                ))
                .toList();

        List<SellerDashboardResponse.RecentOrder> recentOrders = dashboardRepository
                .findRecentOrders(shop.getId())
                .stream()
                .map(row -> new SellerDashboardResponse.RecentOrder(
                        row.getOrderId(),
                        row.getOrderCode(),
                        productSummary(row.getProductName(), row.getItemCount()),
                        zeroIfNull(row.getTotalAmount()),
                        row.getStatus(),
                        row.getPlacedAt() == null
                                ? null
                                : row.getPlacedAt().atOffset(BUSINESS_OFFSET)
                ))
                .toList();

        return new SellerDashboardResponse(
                month.toString(),
                month.lengthOfMonth(),
                orderCount,
                revenue,
                zeroIfNull(wallet.getAvailableBalance()),
                zeroIfNull(wallet.getHoldBalance()),
                productRepository.countByShopIdAndStatus(shop.getId(), "ACTIVE"),
                dailyRevenue,
                statusCounts,
                recentOrders
        );
    }

    private YearMonth parseMonth(String requestedMonth) {
        YearMonth currentMonth = YearMonth.now(BUSINESS_TIMEZONE);
        if (requestedMonth == null || requestedMonth.isBlank()) {
            return currentMonth;
        }

        try {
            YearMonth parsed = YearMonth.parse(requestedMonth.trim());
            if (parsed.isBefore(EARLIEST_ALLOWED_MONTH) || parsed.isAfter(currentMonth)) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            return parsed;
        } catch (DateTimeException exception) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private String productSummary(String productName, Long itemCount) {
        String safeName = productName == null || productName.isBlank() ? "Đơn hàng" : productName;
        long extraItems = Math.max(0L, (itemCount == null ? 0L : itemCount) - 1L);
        return extraItems == 0 ? safeName : safeName + " +" + extraItems + " mục";
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
