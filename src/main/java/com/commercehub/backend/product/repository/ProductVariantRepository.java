package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductIdAndStatusOrderBySortOrderAsc(Long productId, String status);


    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockCount = COALESCE(v.stockCount, 0) + :delta WHERE v.id = :variantId")
    void incrementStockCount(@Param("variantId") Long variantId, @Param("delta") int delta);
}