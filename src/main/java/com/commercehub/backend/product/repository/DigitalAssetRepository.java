package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.DigitalAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, Long> {
    List<DigitalAsset> findByProductVariantIdOrderByCreatedAtDesc(Long variantId);
    long countByProductVariantIdAndStatus(Long variantId, String status);

}