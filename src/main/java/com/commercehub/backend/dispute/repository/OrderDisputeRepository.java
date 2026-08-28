package com.commercehub.backend.dispute.repository;

import com.commercehub.backend.dispute.entity.OrderDispute;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface OrderDisputeRepository
        extends JpaRepository<OrderDispute, Long> {

    boolean existsByOrderItemId(Long orderItemId);

    Optional<OrderDispute> findByOrderItemId(
            Long orderItemId
    );

    List<OrderDispute> findByOrderItemIdIn(List<Long> orderItemIds);

    @Query("""
            SELECT DISTINCT d.orderId
            FROM OrderDispute d
            WHERE d.orderId IN :orderIds
              AND d.status IN :statuses
            """)
    Set<Long> findOrderIdsWithStatuses(
            @Param("orderIds") List<Long> orderIds,
            @Param("statuses") Set<String> statuses
    );

    Optional<OrderDispute> findByOrderIdAndOrderItemId(
            Long orderId,
            Long orderItemId
    );

    // LOCK

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM OrderDispute d
            WHERE d.id = :id
            """)
    Optional<OrderDispute> findByIdWithLock(
            @Param("id") Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM OrderDispute d
            WHERE d.orderItemId = :orderItemId
            """)
    Optional<OrderDispute> findByOrderItemIdWithLock(
            @Param("orderItemId") Long orderItemId
    );

    // BUYER OWNERSHIP

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM orders o
                        JOIN order_items oi
                            ON oi.order_id = o.id
                        WHERE o.id = :orderId
                          AND oi.id = :orderItemId
                          AND o.user_id = :buyerId
                    )
                    """,
            nativeQuery = true
    )
    boolean buyerOwnsOrderItem(
            @Param("buyerId") Long buyerId,
            @Param("orderId") Long orderId,
            @Param("orderItemId") Long orderItemId
    );

    // SELLER OWNERSHIP

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM orders o
                        JOIN order_items oi
                            ON oi.order_id = o.id
                        JOIN shops s
                            ON s.id = o.shop_id
                        WHERE o.id = :orderId
                          AND oi.id = :orderItemId
                          AND s.owner_id = :sellerId
                    )
                    """,
            nativeQuery = true
    )
    boolean sellerOwnsOrderItem(
            @Param("sellerId") Long sellerId,
            @Param("orderId") Long orderId,
            @Param("orderItemId") Long orderItemId
    );

    // CONTEXT

    @Query(
            value = """
                    SELECT o.user_id
                    FROM orders o
                    JOIN order_items oi
                        ON oi.order_id = o.id
                    WHERE o.id = :orderId
                      AND oi.id = :orderItemId
                    """,
            nativeQuery = true
    )
    Optional<Long> findBuyerId(
            @Param("orderId") Long orderId,
            @Param("orderItemId") Long orderItemId
    );

    @Query(
            value = """
                    SELECT o.shop_id
                    FROM orders o
                    JOIN order_items oi
                        ON oi.order_id = o.id
                    WHERE o.id = :orderId
                      AND oi.id = :orderItemId
                    """,
            nativeQuery = true
    )
    Optional<Long> findShopId(
            @Param("orderId") Long orderId,
            @Param("orderItemId") Long orderItemId
    );

    @Query(
            value = """
                    SELECT owner_id
                    FROM shops
                    WHERE id = :shopId
                    """,
            nativeQuery = true
    )
    Optional<Long> findShopOwnerId(
            @Param("shopId") Long shopId
    );

    // FEE LEDGER <-> DISPUTE

    @Modifying
    @Query(value = """
                    UPDATE platform_fee_ledgers
                    SET dispute_id = :disputeId,
                        updated_at = NOW()
                    WHERE order_item_id = :orderItemId
                    """,
            nativeQuery = true
    )
    int linkFeeLedgerToDispute(
            @Param("orderItemId") Long orderItemId,
            @Param("disputeId") Long disputeId
    );


    @Query(value = """
                SELECT id
                FROM shops
                WHERE owner_id = :ownerId
                """,
            nativeQuery = true
    )
    Optional<Long> findShopIdByOwnerId(
            @Param("ownerId") Long ownerId
    );

    // LIST

    Page<OrderDispute> findByUserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    Page<OrderDispute> findByShopIdOrderByCreatedAtDesc(
            Long shopId,
            Pageable pageable
    );

    Page<OrderDispute> findByStatusOrderByCreatedAtDesc(
            String status,
            Pageable pageable
    );

    Page<OrderDispute> findAllByOrderByCreatedAtDesc(
            Pageable pageable
    );

    @Query("""
            SELECT d.id
            FROM OrderDispute d
            WHERE d.status = :status
              AND d.deadlineAt <= :now
            ORDER BY d.deadlineAt ASC, d.id ASC
            """)
    List<Long> findExpiredIds(
            @Param("status") String status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );
}
