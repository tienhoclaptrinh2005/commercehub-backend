package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderStatus;
import com.commercehub.backend.dispute.entity.DisputeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"shop", "shop.owner"})
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.user.id = :userId
              AND (:orderCode = '' OR LOWER(o.orderCode) LIKE CONCAT('%', LOWER(:orderCode), '%'))
              AND o.placedAt >= :fromDateTime
              AND o.placedAt < :toDateTimeExclusive
              AND (
                    (
                        :activeDisputeOnly = true
                        AND EXISTS (
                            SELECT d.id
                            FROM OrderDispute d
                            WHERE d.orderId = o.id
                              AND d.status IN :activeDisputeStatuses
                        )
                    )
                    OR (
                        :activeDisputeOnly = false
                        AND (:orderStatus IS NULL OR o.status = :orderStatus)
                    )
              )
            ORDER BY o.placedAt DESC, o.id DESC
            """)
    Slice<Order> findFirstBuyerOrders(
            @Param("userId") Long userId,
            @Param("orderCode") String orderCode,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("activeDisputeOnly") boolean activeDisputeOnly,
            @Param("fromDateTime") OffsetDateTime fromDateTime,
            @Param("toDateTimeExclusive") OffsetDateTime toDateTimeExclusive,
            @Param("activeDisputeStatuses") Set<DisputeStatus> activeDisputeStatuses,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"shop", "shop.owner"})
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.user.id = :userId
              AND (:orderCode = '' OR LOWER(o.orderCode) LIKE CONCAT('%', LOWER(:orderCode), '%'))
              AND o.placedAt >= :fromDateTime
              AND o.placedAt < :toDateTimeExclusive
              AND (
                    o.placedAt < :beforePlacedAt
                    OR (o.placedAt = :beforePlacedAt AND o.id < :beforeId)
              )
              AND (
                    (
                        :activeDisputeOnly = true
                        AND EXISTS (
                            SELECT d.id
                            FROM OrderDispute d
                            WHERE d.orderId = o.id
                              AND d.status IN :activeDisputeStatuses
                        )
                    )
                    OR (
                        :activeDisputeOnly = false
                        AND (:orderStatus IS NULL OR o.status = :orderStatus)
                    )
              )
            ORDER BY o.placedAt DESC, o.id DESC
            """)
    Slice<Order> findBuyerOrdersBefore(
            @Param("userId") Long userId,
            @Param("orderCode") String orderCode,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("activeDisputeOnly") boolean activeDisputeOnly,
            @Param("fromDateTime") OffsetDateTime fromDateTime,
            @Param("toDateTimeExclusive") OffsetDateTime toDateTimeExclusive,
            @Param("beforePlacedAt") OffsetDateTime beforePlacedAt,
            @Param("beforeId") Long beforeId,
            @Param("activeDisputeStatuses") Set<DisputeStatus> activeDisputeStatuses,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"shop", "shop.owner", "user"})
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.shop.id = :shopId
              AND (
                    :search = ''
                    OR LOWER(o.orderCode) LIKE CONCAT('%', LOWER(:search), '%')
                    OR LOWER(o.user.username) LIKE CONCAT('%', LOWER(:search), '%')
              )
              AND (:deliveryType = '' OR o.deliveryType = :deliveryType)
              AND o.placedAt >= :fromDateTime
              AND o.placedAt < :toDateTimeExclusive
              AND (
                    (
                        :activeDisputeOnly = true
                        AND EXISTS (
                            SELECT d.id
                            FROM OrderDispute d
                            WHERE d.orderId = o.id
                              AND d.status IN :activeDisputeStatuses
                        )
                    )
                    OR (
                        :activeDisputeOnly = false
                        AND (:orderStatus IS NULL OR o.status = :orderStatus)
                    )
              )
            ORDER BY o.placedAt DESC, o.id DESC
            """)
    Slice<Order> findFirstSellerOrders(
            @Param("shopId") Long shopId,
            @Param("search") String search,
            @Param("deliveryType") String deliveryType,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("activeDisputeOnly") boolean activeDisputeOnly,
            @Param("fromDateTime") OffsetDateTime fromDateTime,
            @Param("toDateTimeExclusive") OffsetDateTime toDateTimeExclusive,
            @Param("activeDisputeStatuses") Set<DisputeStatus> activeDisputeStatuses,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"shop", "shop.owner", "user"})
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.shop.id = :shopId
              AND (
                    :search = ''
                    OR LOWER(o.orderCode) LIKE CONCAT('%', LOWER(:search), '%')
                    OR LOWER(o.user.username) LIKE CONCAT('%', LOWER(:search), '%')
              )
              AND (:deliveryType = '' OR o.deliveryType = :deliveryType)
              AND o.placedAt >= :fromDateTime
              AND o.placedAt < :toDateTimeExclusive
              AND (
                    o.placedAt < :beforePlacedAt
                    OR (o.placedAt = :beforePlacedAt AND o.id < :beforeId)
              )
              AND (
                    (
                        :activeDisputeOnly = true
                        AND EXISTS (
                            SELECT d.id
                            FROM OrderDispute d
                            WHERE d.orderId = o.id
                              AND d.status IN :activeDisputeStatuses
                        )
                    )
                    OR (
                        :activeDisputeOnly = false
                        AND (:orderStatus IS NULL OR o.status = :orderStatus)
                    )
              )
            ORDER BY o.placedAt DESC, o.id DESC
            """)
    Slice<Order> findSellerOrdersBefore(
            @Param("shopId") Long shopId,
            @Param("search") String search,
            @Param("deliveryType") String deliveryType,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("activeDisputeOnly") boolean activeDisputeOnly,
            @Param("fromDateTime") OffsetDateTime fromDateTime,
            @Param("toDateTimeExclusive") OffsetDateTime toDateTimeExclusive,
            @Param("beforePlacedAt") OffsetDateTime beforePlacedAt,
            @Param("beforeId") Long beforeId,
            @Param("activeDisputeStatuses") Set<DisputeStatus> activeDisputeStatuses,
            Pageable pageable
    );

    /** Buyer chỉ tra cứu được orderCode thuộc chính tài khoản của mình. */
    @EntityGraph(attributePaths = {"shop", "shop.owner"})
    Optional<Order> findByOrderCodeAndUserId(String orderCode, Long userId);

    /** Seller chỉ tra cứu được ID đơn thuộc chính gian hàng, tránh lộ dữ liệu qua ID tuần tự. */
    @EntityGraph(attributePaths = {"shop", "shop.owner", "user"})
    Optional<Order> findByIdAndShopId(Long id, Long shopId);

    @Query("SELECT o FROM Order o WHERE o.id IN :orderIds AND o.user.id = :buyerId")
    List<Order> findCheckoutOrdersForBuyer(
            @Param("buyerId") Long buyerId,
            @Param("orderIds") List<Long> orderIds
    );


    //  Hàm tìm các đơn hàng Shop ĐÃ NHẬN nhưng xử lý quá hạn (quá 24h)
    @Query("SELECT o.id FROM Order o WHERE o.deliveryType = 'PRE_ORDER' AND o.status = :status AND o.processingDeadlineAt < :deadline ORDER BY o.processingDeadlineAt ASC, o.id ASC")
    List<Long> findExpiredProcessingIds(@Param("status") OrderStatus status, @Param("deadline") OffsetDateTime deadline, Pageable pageable);

    // Hàm tìm các đơn hàng Shop BỎ QUÊN không thèm duyệt
    @Query("SELECT o.id FROM Order o WHERE o.deliveryType = 'PRE_ORDER' AND o.status = :status AND o.approvalDeadlineAt < :deadline ORDER BY o.approvalDeadlineAt ASC, o.id ASC")
    List<Long> findExpiredApprovalIds(@Param("status") OrderStatus status, @Param("deadline") OffsetDateTime deadline, Pageable pageable);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdWithLock(@Param("orderId") Long orderId);

    /** Tra cứu các đơn đã tạo bởi 1 lần checkout (idempotency). */
    List<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /**
     * Đếm số đơn mua đã hoàn tất để hiển thị trên profile buyer.
     *
     * Một đơn chỉ được tính khi đã giao, đã thanh toán và MỌI OrderItem đều có
     * HoldRelease ở trạng thái RELEASED. Điều kiện này loại các đơn còn trong
     * T+7, đang tranh chấp hoặc đã hoàn tiền theo từng item.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM orders o
            WHERE o.user_id = :userId
              AND o.status = 'DELIVERED'
              AND o.payment_status = 'PAID'
              AND EXISTS (
                  SELECT 1
                  FROM order_items oi
                  WHERE oi.order_id = o.id
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM order_items oi
                  WHERE oi.order_id = o.id
                    AND NOT EXISTS (
                        SELECT 1
                        FROM hold_releases hr
                        WHERE hr.order_item_id = oi.id
                          AND hr.status = 'RELEASED'
                    )
              )
            """, nativeQuery = true)
    long countCompletedPurchasesByUserId(@Param("userId") Long userId);

    /**
     * Đếm số đơn bán đã quyết toán thành công của một shop.
     * Dùng cùng định nghĩa hoàn tất với số đơn mua của buyer.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM orders o
            WHERE o.shop_id = :shopId
              AND o.status = 'DELIVERED'
              AND o.payment_status = 'PAID'
              AND EXISTS (
                  SELECT 1
                  FROM order_items oi
                  WHERE oi.order_id = o.id
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM order_items oi
                  WHERE oi.order_id = o.id
                    AND NOT EXISTS (
                        SELECT 1
                        FROM hold_releases hr
                        WHERE hr.order_item_id = oi.id
                          AND hr.status = 'RELEASED'
                    )
              )
            """, nativeQuery = true)
    long countSuccessfulSalesByShopId(@Param("shopId") Long shopId);


}
