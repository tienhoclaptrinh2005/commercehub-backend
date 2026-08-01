package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.Product; // Kiểm tra kỹ dòng import này!
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> , JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = {"shop", "category", "variants", "images"})

    Optional<Product> findBySlugAndStatusNot(String slug, String status);

    @EntityGraph(attributePaths = {"shop", "category"})
    List<Product> findAllByShopIdAndStatusNot(Long shopId, String status);

    List<Product> findAllByCategoryIdAndStatus(Long categoryId, String status);

    boolean existsByNameAndShopId(String name, Long shopId);

    boolean existsBySlug(String slug);
    long countByShopIdAndStatusNot(Long shopId, String status);

    @EntityGraph(attributePaths = {"shop", "category"})
    Page<Product> findByStatus(String status, Pageable pageable);
    boolean existsByNameAndShopIdAndStatusNot(String name, Long shopId, String status);

    @Override
    @EntityGraph(attributePaths = {"shop", "category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);


    @EntityGraph(attributePaths = {"shop", "category"})
    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' AND p.shop.status = 'ACTIVE'")
    Page<Product> findActiveProductsFromActiveShops(Pageable pageable);

}