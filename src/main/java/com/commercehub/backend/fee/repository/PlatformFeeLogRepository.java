package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.PlatformFeeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformFeeLogRepository extends JpaRepository<PlatformFeeLog, Long> {

    // Xem audit trail của 1 ledger
    List<PlatformFeeLog> findByFeeLedgerIdOrderByCreatedAtDesc(Long feeLedgerId);

}