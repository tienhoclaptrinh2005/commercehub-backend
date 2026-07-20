package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.PlatformFeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformFeeConfigRepository extends JpaRepository<PlatformFeeConfig, Long> {
    // Tìm cấu hình phí đang được áp dụng hiện tại
    Optional<PlatformFeeConfig> findByIsActiveTrue();
}