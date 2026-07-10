package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.HoldRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface HoldReleaseRepository extends JpaRepository<HoldRelease, Long> {

    // Tìm các khoản Hold đã đến hạn giải phóng
    @Query("SELECT h FROM HoldRelease h WHERE h.status = 'HOLDING' AND h.scheduledReleaseAt <= :now")
    List<HoldRelease> findDueReleases(@Param("now") OffsetDateTime now);

    HoldRelease findByOrderItemId(Long orderItemId);


    HoldRelease findByFeeLedgerId(Long feeLedgerId);
}