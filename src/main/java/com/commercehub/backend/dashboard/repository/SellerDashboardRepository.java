package com.commercehub.backend.dashboard.repository;

import com.commercehub.backend.order.entity.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

    interface PreOrderWorkloadProjection {
        Long getNewRequestCount();

        Long getProcessingCount();
    }

    interface SellerNotificationProjection {
        Long getRecentInstantOrderCount();

        Long getNewPreOrderRequestCount();

        Long getProcessingPreOrderCount();

        Long getActiveDisputeCount();

        Long getWithdrawalUpdateCount();
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
                           WHEN status IN ('REJECTED', 'CANCELLED')
                                OR payment_status NOT IN ('PAID', 'PARTIALLY_REFUNDED')
                               THEN 0
                           WHEN payment_status = 'PARTIALLY_REFUNDED'
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
            SELECT o.status AS "status",
                   COUNT(*)::BIGINT AS "count"
            FROM orders o
            WHERE o.shop_id = :shopId
              AND o.placed_at >= :fromTime
              AND o.placed_at < :toTime
            GROUP BY o.status
            ORDER BY COUNT(*) DESC, o.status
            """, nativeQuery = true)
    List<OrderStatusCountProjection> findMonthlyOrderStatusCounts(
            @Param("shopId") Long shopId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime
    );

    /**
     * Hàng đợi công việc hiện tại của seller, không phụ thuộc tháng đang xem.
     * Một truy vấn tổng hợp thay cho hai câu COUNT riêng biệt.
     */
    @Query(value = """
            SELECT COUNT(*) FILTER (
                       WHERE o.delivery_type = 'PRE_ORDER'
                         AND o.status = 'WAITING_SELLER_ACCEPTANCE'
                   )::BIGINT AS "newRequestCount",
                   COUNT(*) FILTER (
                       WHERE o.delivery_type = 'PRE_ORDER'
                         AND o.status = 'PROCESSING'
                   )::BIGINT AS "processingCount"
            FROM orders o
            WHERE o.shop_id = :shopId
              AND o.delivery_type = 'PRE_ORDER'
              AND o.status IN ('WAITING_SELLER_ACCEPTANCE', 'PROCESSING')
            """, nativeQuery = true)
    PreOrderWorkloadProjection findCurrentPreOrderWorkload(@Param("shopId") Long shopId);

    /**
     * Badge công việc của seller. Đơn giao ngay chỉ tính trong cửa sổ gần đây để
     * badge không tăng vĩnh viễn; đơn đặt hàng và khiếu nại chỉ tính trạng thái
     * còn cần seller theo dõi/xử lý.
     */
    @Query(value = """
            SELECT COUNT(*) FILTER (
                       WHERE o.delivery_type = 'INSTANT'
                         AND o.placed_at >= :recentSince
                         AND o.placed_at > COALESCE(
                             (
                                 SELECT reads.instant_orders_read_at
                                 FROM seller_notification_reads reads
                                 WHERE reads.seller_id = :sellerId
                             ),
                             TIMESTAMPTZ 'epoch'
                         )
                         AND o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')
                         AND o.status NOT IN ('REJECTED', 'CANCELLED')
                   )::BIGINT AS "recentInstantOrderCount",
                   COUNT(*) FILTER (
                       WHERE o.delivery_type = 'PRE_ORDER'
                         AND o.status = 'WAITING_SELLER_ACCEPTANCE'
                         AND o.placed_at > COALESCE(
                             (
                                 SELECT reads.pre_orders_read_at
                                 FROM seller_notification_reads reads
                                 WHERE reads.seller_id = :sellerId
                             ),
                             TIMESTAMPTZ 'epoch'
                         )
                   )::BIGINT AS "newPreOrderRequestCount",
                   COUNT(*) FILTER (
                       WHERE o.delivery_type = 'PRE_ORDER'
                         AND o.status = 'PROCESSING'
                         AND o.placed_at > COALESCE(
                             (
                                 SELECT reads.pre_orders_read_at
                                 FROM seller_notification_reads reads
                                 WHERE reads.seller_id = :sellerId
                             ),
                             TIMESTAMPTZ 'epoch'
                         )
                   )::BIGINT AS "processingPreOrderCount",
                   (
                       SELECT COUNT(*)::BIGINT
                       FROM order_disputes dispute
                       WHERE dispute.shop_id = :shopId
                         AND dispute.status IN (
                             'OPEN', 'WARRANTY_IN_PROGRESS',
                             'WAITING_BUYER_CONFIRMATION', 'ADMIN_REVIEW'
                         )
                         AND dispute.created_at > COALESCE(
                             (
                                 SELECT reads.disputes_read_at
                                 FROM seller_notification_reads reads
                                 WHERE reads.seller_id = :sellerId
                             ),
                             TIMESTAMPTZ 'epoch'
                         )
                   ) AS "activeDisputeCount",
                   (
                       SELECT COUNT(*)::BIGINT
                       FROM withdrawals withdrawal
                       JOIN wallets withdrawal_wallet
                         ON withdrawal_wallet.id = withdrawal.wallet_id
                       WHERE withdrawal_wallet.user_id = :sellerId
                         AND withdrawal.status IN ('APPROVED', 'DONE', 'REJECTED')
                         AND withdrawal.updated_at > COALESCE(
                             (
                                 SELECT reads.withdrawals_read_at
                                 FROM seller_notification_reads reads
                                 WHERE reads.seller_id = :sellerId
                             ),
                             TIMESTAMPTZ 'epoch'
                         )
                   ) AS "withdrawalUpdateCount"
            FROM orders o
            WHERE o.shop_id = :shopId
            """, nativeQuery = true)
    SellerNotificationProjection findSellerNotificationCounts(
            @Param("shopId") Long shopId,
            @Param("sellerId") Long sellerId,
            @Param("recentSince") OffsetDateTime recentSince
    );

    @Modifying
    @Query(value = """
            INSERT INTO seller_notification_reads (
                seller_id,
                instant_orders_read_at,
                pre_orders_read_at,
                disputes_read_at,
                withdrawals_read_at,
                created_at,
                updated_at
            ) VALUES (
                :sellerId,
                CASE WHEN :category = 'INSTANT_ORDERS' THEN NOW() ELSE TIMESTAMPTZ 'epoch' END,
                CASE WHEN :category = 'PRE_ORDERS' THEN NOW() ELSE TIMESTAMPTZ 'epoch' END,
                CASE WHEN :category = 'DISPUTES' THEN NOW() ELSE TIMESTAMPTZ 'epoch' END,
                CASE WHEN :category = 'WITHDRAWALS' THEN NOW() ELSE TIMESTAMPTZ 'epoch' END,
                NOW(),
                NOW()
            )
            ON CONFLICT (seller_id) DO UPDATE SET
                instant_orders_read_at = CASE
                    WHEN :category = 'INSTANT_ORDERS' THEN NOW()
                    ELSE seller_notification_reads.instant_orders_read_at
                END,
                pre_orders_read_at = CASE
                    WHEN :category = 'PRE_ORDERS' THEN NOW()
                    ELSE seller_notification_reads.pre_orders_read_at
                END,
                disputes_read_at = CASE
                    WHEN :category = 'DISPUTES' THEN NOW()
                    ELSE seller_notification_reads.disputes_read_at
                END,
                withdrawals_read_at = CASE
                    WHEN :category = 'WITHDRAWALS' THEN NOW()
                    ELSE seller_notification_reads.withdrawals_read_at
                END,
                updated_at = NOW()
            """, nativeQuery = true)
    void markSellerNotificationCategoryRead(
            @Param("sellerId") Long sellerId,
            @Param("category") String category
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
                   o.status AS "status",
                   o.placed_at AS "placedAt"
            FROM orders o
            WHERE o.shop_id = :shopId
              AND o.placed_at IS NOT NULL
            ORDER BY o.placed_at DESC NULLS LAST, o.id DESC
            LIMIT 5
            """, nativeQuery = true)
    List<RecentOrderProjection> findRecentOrders(@Param("shopId") Long shopId);
}
