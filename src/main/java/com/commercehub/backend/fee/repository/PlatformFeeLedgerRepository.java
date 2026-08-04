package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.PlatformFeeLedger;
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
public interface PlatformFeeLedgerRepository extends JpaRepository<PlatformFeeLedger, Long> {

    // Seller xem lịch sử phí của shop mình
    Page<PlatformFeeLedger> findByShopIdOrderByCreatedAtDesc(Long shopId, Pageable pageable);

    // Seller xem phí theo tháng
    @Query("SELECT l FROM PlatformFeeLedger l WHERE l.shopId = :shopId " +
           "AND EXTRACT(YEAR FROM l.feeIncurredAt) = :year " +
           "AND EXTRACT(MONTH FROM l.feeIncurredAt) = :month " +
           "ORDER BY l.feeIncurredAt DESC")
    List<PlatformFeeLedger> findByShopIdAndYearMonth(
            @Param("shopId") Long shopId,
            @Param("year") int year,
            @Param("month") int month
    );

    // Admin: xem tất cả ledgers với filter status
    Page<PlatformFeeLedger> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    // Admin: xem ledgers của 1 shop
    Page<PlatformFeeLedger> findByShopIdAndStatusOrderByCreatedAtDesc(
            Long shopId, String status, Pageable pageable
    );

    // Kiểm tra ledger theo orderItemId (cho case duplicate prevention)
    Optional<PlatformFeeLedger> findByOrderItemId(Long orderItemId);

    // Lock khi cần update status (đảm bảo concurrent safety)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM PlatformFeeLedger l WHERE l.id = :id")
    Optional<PlatformFeeLedger> findByIdWithLock(@Param("id") Long id);

    // Admin stats: tổng phí theo status
    @Query("SELECT l.status, SUM(l.feeAmount) FROM PlatformFeeLedger l " +
           "WHERE EXTRACT(YEAR FROM l.feeIncurredAt) = :year " +
           "AND EXTRACT(MONTH FROM l.feeIncurredAt) = :month " +
           "GROUP BY l.status")
    List<Object[]> sumFeeByStatusAndYearMonth(@Param("year") int year, @Param("month") int month);
}