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

    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END " +
            "FROM ProductVariant v " +
            "WHERE v.product.id = :productId " +
            "AND LOWER(TRIM(v.name)) = LOWER(TRIM(:name))")
    boolean existsByNormalizedName(@Param("productId") Long productId,
                                   @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END " +
            "FROM ProductVariant v " +
            "WHERE v.product.id = :productId " +
            "AND v.id <> :id " +
            "AND LOWER(TRIM(v.name)) = LOWER(TRIM(:name))")
    boolean existsByNormalizedNameAndIdNot(@Param("productId") Long productId,
                                           @Param("name") String name,
                                           @Param("id") Long id);


    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockCount = COALESCE(v.stockCount, 0) + :delta WHERE v.id = :variantId")
    void incrementStockCount(@Param("variantId") Long variantId, @Param("delta") int delta);
}
