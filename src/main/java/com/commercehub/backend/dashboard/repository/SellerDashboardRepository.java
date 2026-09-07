package com.commercehub.backend.dashboard.repository;

import com.commercehub.backend.order.entity.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public interface SellerDashboardRepository extends Repository<Order, Long> {

    interface DailyRevenueProjection {
        Integer getDay();

        Long getOrderCount();

        BigDecimal getRevenue();
    }

    interface OrderStatusCountProjection {
        String getStatus();

        Long getCount();
    }

    interface RecentOrderProjection {
        Long getOrderId();

        String getOrderCode();

        String getProductName();

        Long getItemCount();

        BigDecimal getTotalAmount();

        String getStatus();

        Instant getPlacedAt();
    }

    /**
     * Tổng hợp ngay tại database để mỗi tháng chỉ trả tối đa 31 dòng.
     * Đơn hoàn một phần được phân bổ giảm giá theo tỷ lệ giá trị item còn lại.
     */
    @Query(value = """
            WITH scoped_orders AS (
                SELECT o.id,
                       o.placed_at,
                       o.status,
                       o.payment_status,
                       o.total_amount,
                       COALESCE(SUM(oi.line_total), 0) AS original_item_total,
                       COALESCE(
                           SUM(oi.line_total) FILTER (
                               WHERE COALESCE(oi.refund_status, 'NONE') <> 'REFUNDED'
                           ),
                           0
                       ) AS retained_item_total
                FROM orders o
                LEFT JOIN order_items oi ON oi.order_id = o.id
                WHERE o.shop_id = :shopId
                  AND o.placed_at >= :fromTime
                  AND o.placed_at < :toTime
                GROUP BY o.id, o.placed_at, o.status, o.payment_status, o.total_amount
            ), valued_orders AS (
                SELECT placed_at,
                       CASE
                           WHEN status IN (
                               'REJECTED', 'REFUNDED', 'CANCELLED',
                               'CANCELLED_BY_SELLER', 'CANCELLED_BY_SYSTEM'
                           ) OR payment_status NOT IN ('PAID', 'PARTIAL_REFUND')
                               THEN 0
                           WHEN payment_status = 'PARTIAL_REFUND'
                               THEN CASE
                                   WHEN original_item_total <= 0 THEN 0
                                   ELSE ROUND(
                                       total_amount * retained_item_total / original_item_total,
                                       2
                                   )
                               END
                           ELSE total_amount
                       END AS revenue
                FROM scoped_orders
            )
            SELECT EXTRACT(
                       DAY FROM placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh'
                   )::INTEGER AS "day",
                   COUNT(*)::BIGINT AS "orderCount",
                   COALESCE(SUM(revenue), 0) AS "revenue"
            FROM valued_orders
            GROUP BY EXTRACT(DAY FROM placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
            ORDER BY "day"
            """, nativeQuery = true)
    List<DailyRevenueProjection> findMonthlyRevenue(
            @Param("shopId") Long shopId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime
    );

    @Query(value = """
            WITH effective_orders AS (
                SELECT CASE
                           WHEN EXISTS (
                               SELECT 1
                               FROM order_disputes dispute
                               WHERE dispute.order_id = o.id
                                 AND dispute.status IN (
                                     'OPEN', 'WARRANTY_IN_PROGRESS',
                                     'WAITING_BUYER_CONFIRMATION', 'PROCESSING'
                                 )
                           ) THEN 'DISPUTED'
                           ELSE o.status
                       END AS effective_status
                FROM orders o
                WHERE o.shop_id = :shopId
                  AND o.placed_at >= :fromTime
                  AND o.placed_at < :toTime
            )
            SELECT effective_status AS "status",
                   COUNT(*)::BIGINT AS "count"
            FROM effective_orders
            GROUP BY effective_status
            ORDER BY COUNT(*) DESC, effective_status
            """, nativeQuery = true)
    List<OrderStatusCountProjection> findMonthlyOrderStatusCounts(
            @Param("shopId") Long shopId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime
    );

    @Query(value = """
            SELECT o.id AS "orderId",
                   o.order_code AS "orderCode",
                   COALESCE(
                       (
                           SELECT oi.product_name
                           FROM order_items oi
                           WHERE oi.order_id = o.id
                           ORDER BY oi.id
                           LIMIT 1
                       ),
                       'Đơn hàng'
                   ) AS "productName",
                   (
                       SELECT COUNT(*)
                       FROM order_items oi
                       WHERE oi.order_id = o.id
                   )::BIGINT AS "itemCount",
                   o.total_amount AS "totalAmount",
                   CASE
                       WHEN EXISTS (
                           SELECT 1
                           FROM order_disputes dispute
                           WHERE dispute.order_id = o.id
                             AND dispute.status IN (
                                 'OPEN', 'WARRANTY_IN_PROGRESS',
                                 'WAITING_BUYER_CONFIRMATION', 'PROCESSING'
                             )
                       ) THEN 'DISPUTED'
                       ELSE o.status
                   END AS "status",
                   o.placed_at AS "placedAt"
            FROM orders o
            WHERE o.shop_id = :shopId
              AND o.placed_at IS NOT NULL
            ORDER BY o.placed_at DESC NULLS LAST, o.id DESC
            LIMIT 5
            """, nativeQuery = true)
    List<RecentOrderProjection> findRecentOrders(@Param("shopId") Long shopId);
}
