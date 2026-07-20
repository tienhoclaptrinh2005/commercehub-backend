package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.PlatformFeeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformFeeLogRepository extends JpaRepository<PlatformFeeLog, Long> {
}