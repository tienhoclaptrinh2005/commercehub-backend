package com.commercehub.backend.wallet.repository;

import com.commercehub.backend.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
    Optional<Wallet> findByUserIdWithLock(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.isPlatform = true")
    Optional<Wallet> findPlatformWalletWithLock();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id IN :userIds ORDER BY w.id ASC")
    List<Wallet> findAllByUserIdsWithLock(@Param("userIds") Collection<Long> userIds);

    /** Kiểm tra ví platform đã tồn tại chưa (dùng cho seeder lúc khởi động). */
    boolean existsByIsPlatformTrue();

}
