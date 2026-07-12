package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.DigitalAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

@Repository
public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, Long> {
    List<DigitalAsset> findByProductVariantIdOrderByCreatedAtDesc(Long variantId);
    long countByProductVariantIdAndStatus(Long variantId, String status);

    @Query(value = "SELECT * FROM digital_assets " +
            "WHERE product_variant_id = :variantId AND status = 'AVAILABLE' " +
            "LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<DigitalAsset> findAvailableAssetsWithLock(@Param("variantId") Long variantId, @Param("limit") int limit);

}