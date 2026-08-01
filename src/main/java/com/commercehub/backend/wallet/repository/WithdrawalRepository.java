package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.Withdrawal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {


    Page<Withdrawal> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    // Dành cho Admin: Xem tất cả yêu cầu rút tiền theo trạng thái
    Page<Withdrawal> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Withdrawal w JOIN FETCH w.wallet wl JOIN FETCH wl.user WHERE w.id = :id")
    Optional<Withdrawal> findByIdWithLock(@Param("id") Long id);
}