package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.PreOrderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PreOrderConfigRepository extends JpaRepository<PreOrderConfig, Long> {

    Optional<PreOrderConfig> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    void deleteByProductId(Long productId);
}