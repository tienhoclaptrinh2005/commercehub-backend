package com.commercehub.backend.voucher.repository;

import com.commercehub.backend.voucher.entity.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    boolean existsByShopIdAndCodeIgnoreCase(Long shopId, String code);

    @EntityGraph(attributePaths = {"products"})
    @Query("SELECT voucher FROM Voucher voucher WHERE voucher.id = :id AND voucher.shop.id = :shopId")
    Optional<Voucher> findOwnedById(@Param("shopId") Long shopId, @Param("id") Long id);

    @EntityGraph(attributePaths = {"products"})
    @Query("SELECT voucher FROM Voucher voucher WHERE voucher.shop.id = :shopId AND UPPER(voucher.code) = :code")
    Optional<Voucher> findByShopIdAndNormalizedCode(@Param("shopId") Long shopId, @Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT voucher FROM Voucher voucher WHERE voucher.shop.id = :shopId AND UPPER(voucher.code) = :code")
    Optional<Voucher> findForUpdate(@Param("shopId") Long shopId, @Param("code") String code);

    @Query("""
            SELECT voucher FROM Voucher voucher
            WHERE voucher.shop.id = :shopId
              AND (:keyword = '' OR LOWER(voucher.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(voucher.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Voucher> findSellerVouchers(@Param("shopId") Long shopId,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);
}
