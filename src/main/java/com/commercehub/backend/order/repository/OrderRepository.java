package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"shop"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"shop"})
    Page<Order> findByUserIdAndOrderCodeContainingIgnoreCase(
            Long userId,
            String orderCode,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"shop"})
    Page<Order> findByShopId(Long shopId, Pageable pageable);


    //  Hàm tìm các đơn hàng Shop ĐÃ NHẬN nhưng xử lý quá hạn (quá 24h)
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.processingDeadlineAt < :deadline ORDER BY o.processingDeadlineAt ASC, o.id ASC")
    List<Long> findExpiredProcessingIds(@Param("status") String status, @Param("deadline") OffsetDateTime deadline, Pageable pageable);

    // Hàm tìm các đơn hàng Shop BỎ QUÊN không thèm duyệt
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.approvalDeadlineAt < :deadline ORDER BY o.approvalDeadlineAt ASC, o.id ASC")
    List<Long> findExpiredApprovalIds(@Param("status") String status, @Param("deadline") OffsetDateTime deadline, Pageable pageable);


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
