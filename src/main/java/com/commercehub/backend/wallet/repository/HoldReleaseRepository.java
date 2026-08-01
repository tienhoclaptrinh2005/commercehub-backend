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

    @Query("SELECT h FROM HoldRelease h " +
            "JOIN FETCH h.wallet w " +
            "JOIN FETCH w.user " +
            "WHERE h.status = 'HOLDING' AND h.scheduledReleaseAt <= :now")
    List<HoldRelease> findDueReleases(@Param("now") OffsetDateTime now);
    HoldRelease findByOrderItemId(Long orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HoldRelease h WHERE h.id = :id")
    Optional<HoldRelease> findByIdWithLock(@Param("id") Long id);

    HoldRelease findByFeeLedgerId(Long feeLedgerId);
}