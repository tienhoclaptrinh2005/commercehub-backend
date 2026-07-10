package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.Deposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DepositRepository extends JpaRepository<Deposit, Long> {
    Optional<Deposit> findByTransactionCode(String transactionCode);
}