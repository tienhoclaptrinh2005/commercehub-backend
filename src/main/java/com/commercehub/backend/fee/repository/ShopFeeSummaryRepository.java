package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.ShopFeeSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopFeeSummaryRepository extends JpaRepository<ShopFeeSummary, Long> {
    Optional<ShopFeeSummary> findByShopIdAndPeriodYearAndPeriodMonth(Long shopId, Integer year, Integer month);
}