package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.PlatformFeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformFeeConfigRepository extends JpaRepository<PlatformFeeConfig, Long> {
    // Tìm cấu hình phí đang được áp dụng hiện tại
    Optional<PlatformFeeConfig> findByIsActiveTrue();

    /**
     * Deactivate TẤT CẢ config đang active bằng 1 câu UPDATE nguyên tử —
     * chống race khi 2 admin cùng tạo config mới (kết hợp với partial unique
     * index uq_fee_config_active trên DB).
     */
    @Modifying
    @Query("UPDATE PlatformFeeConfig c SET c.isActive = false, c.effectiveUntil = :now WHERE c.isActive = true")
    int deactivateAllActive(@Param("now") OffsetDateTime now);

    // Lấy tất cả config theo thứ tự mới nhất (Admin xem danh sách)
    List<PlatformFeeConfig> findAllByOrderByCreatedAtDesc();

    // Lấy config theo ID với validation Admin
    Optional<PlatformFeeConfig> findByIdAndIsActiveTrue(Long id);

    // Kiểm tra có config active nào khác không (để validate khi tạo mới)
    boolean existsByIsActiveTrue();

}