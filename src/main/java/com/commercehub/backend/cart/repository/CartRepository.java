package com.commercehub.backend.cart.repository;

import com.commercehub.backend.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /** Lấy giỏ kèm items + variant + product trong 1 query (tránh N+1). */
    @Query("SELECT DISTINCT c FROM Cart c " +
           "LEFT JOIN FETCH c.items i " +
           "LEFT JOIN FETCH i.productVariant v " +
           "LEFT JOIN FETCH v.product p " +
           "WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);
}
