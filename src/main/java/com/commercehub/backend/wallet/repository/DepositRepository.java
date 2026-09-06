package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.Deposit;
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
public interface DepositRepository extends JpaRepository<Deposit, Long> {
    Optional<Deposit> findByTransactionCode(String transactionCode);

    boolean existsByProviderTransactionId(String providerTransactionId);

    Page<Deposit> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Deposit d WHERE d.transactionCode = :txCode")
    Optional<Deposit> findByTransactionCodeWithLock(@Param("txCode") String txCode);
}
