package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.HoldRelease;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoldReleaseRepository extends JpaRepository<HoldRelease, Long> {

    /**
     * Scheduler chỉ lấy những khoản đang HOLDING và đã đến hạn.
     *
     * COMPLAINED, WARRANTY_IN_PROGRESS, DISPUTED
     * sẽ KHÔNG được scheduler xử lý.
     */
    @Query("""
            SELECT h.id
            FROM HoldRelease h
            WHERE h.status = 'HOLDING'
              AND h.scheduledReleaseAt <= :now
            ORDER BY h.scheduledReleaseAt ASC, h.id ASC
            """)
    List<Long> findDueReleaseIds(
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );


    /**
     * Một Order có thể có nhiều OrderItem,
     * mỗi OrderItem có một HoldRelease.
     */
    List<HoldRelease> findByOrderId(Long orderId);


    /**
     * Chỉ đọc HoldRelease theo OrderItem.
     * Không dùng lock.
     */
    Optional<HoldRelease> findByOrderItemId(Long orderItemId);

    List<HoldRelease> findByOrderItemIdIn(List<Long> orderItemIds);


    /**
     * Lock theo HoldRelease ID.
     *
     * Dùng trong:
     * - HoldReleaseProcessor
     * - Admin resolve dispute
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT h
            FROM HoldRelease h
            JOIN FETCH h.wallet w
            JOIN FETCH w.user
            WHERE h.id = :id
            """)
    Optional<HoldRelease> findByIdWithLock(
            @Param("id") Long id
    );


    /**
     * Lock theo OrderItem.
     *
     * Dùng cho:
     * - Buyer complain
     * - Seller start warranty
     * - Seller complete warranty
     * - Escalate dispute
     *
     * PESSIMISTIC_WRITE giúp tránh race condition
     * với scheduler nhả tiền.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT h
            FROM HoldRelease h
            WHERE h.orderItemId = :orderItemId
            """)
    Optional<HoldRelease> findByOrderItemIdWithLock(
            @Param("orderItemId") Long orderItemId
    );


    /**
     * Tìm HoldRelease liên kết với Fee Ledger.
     */
    Optional<HoldRelease> findByFeeLedgerId(Long feeLedgerId);

}
