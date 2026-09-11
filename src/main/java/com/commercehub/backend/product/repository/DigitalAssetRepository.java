package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.DigitalAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.List;

@Repository
public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, Long> {
    Slice<DigitalAsset> findByProductVariantIdAndStatus(Long variantId, String status, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO digital_assets (
                product_variant_id,
                asset_type,
                delivery_content,
                asset_data,
                asset_identifier,
                content_hash,
                status,
                is_delivered,
                created_at,
                updated_at
            ) VALUES (
                :variantId,
                :assetType,
                :content,
                :content,
                :assetIdentifier,
                :contentHash,
                'AVAILABLE',
                FALSE,
                NOW(),
                NOW()
            )
            ON CONFLICT (content_hash) DO NOTHING
            """, nativeQuery = true)
    int insertAvailableAssetIfAbsent(
            @Param("variantId") Long variantId,
            @Param("assetType") String assetType,
            @Param("content") String content,
            @Param("assetIdentifier") String assetIdentifier,
            @Param("contentHash") String contentHash
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DigitalAsset asset " +
            "WHERE asset.productVariant.id = :variantId AND asset.status = 'AVAILABLE'")
    int deleteAvailableAssetsByVariantId(@Param("variantId") Long variantId);

    @Query("SELECT asset FROM DigitalAsset asset " +
            "WHERE asset.productVariant.id = :variantId " +
            "AND asset.status = 'AVAILABLE' " +
            "AND asset.id > :afterId " +
            "ORDER BY asset.id ASC")
    List<DigitalAsset> findAvailableAssetsAfterId(
            @Param("variantId") Long variantId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Query(value = "SELECT * FROM digital_assets " +
            "WHERE product_variant_id = :variantId AND status = 'AVAILABLE' " +
            "LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<DigitalAsset> findAvailableAssetsWithLock(@Param("variantId") Long variantId, @Param("limit") int limit);

    @Query("SELECT da FROM DigitalAsset da WHERE da.orderItemId IN " +
            "(SELECT oi.id FROM OrderItem oi WHERE oi.order.id = :orderId)")
    List<DigitalAsset> findDeliveredAssetsByOrderId(@Param("orderId") Long orderId);

}
