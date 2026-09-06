package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.Deposit;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.time.OffsetDateTime;

@Repository
public interface DepositRepository extends JpaRepository<Deposit, Long> {
    Optional<Deposit> findByTransactionCode(String transactionCode);

    Optional<Deposit> findByTransactionCodeAndUserId(String transactionCode, Long userId);

    Optional<Deposit> findByIdempotencyKey(String idempotencyKey);

    Optional<Deposit> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    Optional<Deposit> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByProviderTransactionId(String providerTransactionId);

    boolean existsByTransactionCode(String transactionCode);

    long countByUserIdAndCreatedAtAfter(Long userId, OffsetDateTime createdAfter);

    Page<Deposit> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Deposit d WHERE d.transactionCode = :txCode")
    Optional<Deposit> findByTransactionCodeWithLock(@Param("txCode") String txCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Deposit d
               SET d.status = 'EXPIRED', d.processedAt = :now
             WHERE d.status = 'PENDING' AND d.expiresAt <= :now
            """)
    int expirePendingDeposits(@Param("now") OffsetDateTime now);
}
