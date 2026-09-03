package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.WalletTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    Slice<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    Slice<WalletTransaction> findByWalletIdAndTransactionTypeInOrderByCreatedAtDesc(
            Long walletId,
            Collection<String> transactionTypes,
            Pageable pageable
    );
}
