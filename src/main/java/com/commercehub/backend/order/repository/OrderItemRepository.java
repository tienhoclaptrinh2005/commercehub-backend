package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByOrderIdIn(List<Long> orderIds);
    List<OrderItem> findByOrder(Order order);

    boolean existsByOrder_UserIdAndProductVariant_Product_IdAndOrder_Status(Long userId, Long productId, String status);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN product_variants pv ON pv.id = oi.product_variant_id
                JOIN hold_releases hr ON hr.order_item_id = oi.id
                WHERE oi.id = :orderItemId
                  AND o.user_id = :userId
                  AND pv.product_id = :productId
                  AND o.status = 'DELIVERED'
                  AND o.payment_status = 'PAID'
                  AND hr.status = 'RELEASED'
                  AND oi.refund_status = 'NONE'
            )
            """, nativeQuery = true)
    boolean isReviewableOrderItem(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("orderItemId") Long orderItemId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.id = :id")
    Optional<OrderItem> findByIdWithLock(@Param("id") Long id);

    long countByOrderId(Long orderId);

    long countByOrderIdAndRefundStatus(Long orderId, String refundStatus);
}
