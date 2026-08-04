package com.commercehub.backend.cart.repository;

import com.commercehub.backend.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndProductVariantId(Long cartId, Long productVariantId);

    /** Lấy item kèm kiểm tra ownership (item phải thuộc giỏ của user). */
    @Query("SELECT i FROM CartItem i WHERE i.id = :itemId AND i.cart.user.id = :userId")
    Optional<CartItem> findByIdAndUserId(@Param("itemId") Long itemId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM CartItem i WHERE i.cart.id = :cartId")
    void deleteAllByCartId(@Param("cartId") Long cartId);
}
