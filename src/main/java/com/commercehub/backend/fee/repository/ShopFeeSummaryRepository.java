package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.ShopFeeSummary;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopFeeSummaryRepository extends JpaRepository<ShopFeeSummary, Long> {

    Optional<ShopFeeSummary> findByShopIdAndPeriodYearAndPeriodMonth(Long shopId, Integer year, Integer month);

    /** Lấy summary với pessimistic lock — dùng khi cộng dồn để chống lost update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShopFeeSummary s WHERE s.shopId = :shopId " +
           "AND s.periodYear = :year AND s.periodMonth = :month")
    Optional<ShopFeeSummary> findByShopIdAndPeriodYearAndPeriodMonthForUpdate(
            @Param("shopId") Long shopId, @Param("year") Integer year, @Param("month") Integer month
    );

    // Admin xem tất cả summary theo tháng
    Page<ShopFeeSummary> findByPeriodYearAndPeriodMonthOrderByTotalFeeDesc(
            Integer year, Integer month, Pageable pageable
    );

    // Top shops theo phí (Admin dashboard)
    @Query("SELECT s FROM ShopFeeSummary s WHERE s.periodYear = :year AND s.periodMonth = :month " +
           "ORDER BY s.totalFee DESC")
    List<ShopFeeSummary> findTopShopsByFee(
            @Param("year") int year, @Param("month") int month, Pageable pageable
    );
}