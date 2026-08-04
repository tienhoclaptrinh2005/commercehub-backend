package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.HoldRelease;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoldReleaseRepository extends JpaRepository<HoldRelease, Long> {

    /** Tìm tất cả HoldRelease HOLDING đã đến hạn để Scheduler xử lý. */
    @Query("SELECT h FROM HoldRelease h " +
            "JOIN FETCH h.wallet w " +
            "JOIN FETCH w.user " +
            "WHERE h.status = 'HOLDING' AND h.scheduledReleaseAt <= :now")
    List<HoldRelease> findDueReleases(@Param("now") OffsetDateTime now);

    /** Tìm tất cả HoldRelease theo orderId (1 order → nhiều item). */
    List<HoldRelease> findByOrderId(Long orderId);

    /** Tìm HoldRelease theo orderItemId (không có lock — dùng cho đọc). */
    Optional<HoldRelease> findByOrderItemId(Long orderItemId);


    /** Tìm HoldRelease theo id với Pessimistic Lock (dùng trong HoldReleaseProcessor và adminResolve). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HoldRelease h JOIN FETCH h.wallet w JOIN FETCH w.user WHERE h.id = :id")
    Optional<HoldRelease> findByIdWithLock(@Param("id") Long id);

    /** Tra cứu HoldRelease từ FeeLedger (dùng cho Admin/reporting). */
    Optional<HoldRelease> findByFeeLedgerId(Long feeLedgerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HoldRelease h WHERE h.orderItemId = :orderItemId")
    Optional<HoldRelease> findByOrderItemIdWithLock(@Param("orderItemId") Long orderItemId);




}
