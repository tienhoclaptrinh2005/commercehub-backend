package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.CheckoutAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT attempt
            FROM CheckoutAttempt attempt
            WHERE attempt.userId = :userId
              AND attempt.operationType = :operationType
              AND attempt.keyValue = :keyValue
            """)
    Optional<CheckoutAttempt> findForUpdate(
            @Param("userId") Long userId,
            @Param("operationType") String operationType,
            @Param("keyValue") String keyValue
    );
}
