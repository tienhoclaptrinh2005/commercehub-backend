package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.Withdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {


    Page<Withdrawal> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    // Dành cho Admin: Xem tất cả yêu cầu rút tiền theo trạng thái
    Page<Withdrawal> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}