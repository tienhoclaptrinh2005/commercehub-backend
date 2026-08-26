package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.Product; // Kiểm tra kỹ dòng import này!
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> , JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "variants", "preOrderConfig"})
    @Query("SELECT p FROM Product p JOIN p.shop.owner.roles ownerRole " +
            "WHERE p.slug = :slug " +
            "AND p.status = 'ACTIVE' " +
            "AND p.shop.status = 'ACTIVE' " +
            "AND p.shop.owner.status = 'ACTIVE' " +
            "AND ownerRole.name = 'SELLER' " +
            "AND p.category.isActive = true " +
            "AND p.category.parent.isActive = true")
    Optional<Product> findPublicBySlug(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "variants", "preOrderConfig"})
    @Query("SELECT p FROM Product p JOIN p.shop.owner.roles ownerRole " +
            "WHERE p.id = :productId " +
            "AND p.status = 'ACTIVE' " +
            "AND p.shop.status = 'ACTIVE' " +
            "AND p.shop.owner.status = 'ACTIVE' " +
            "AND ownerRole.name = 'SELLER' " +
            "AND p.category.isActive = true " +
            "AND p.category.parent.isActive = true")
    Optional<Product> findPublicById(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "preOrderConfig"})
    @Query("SELECT p FROM Product p JOIN p.shop.owner.roles ownerRole " +
            "WHERE p.shop.id = :shopId " +
            "AND p.status = 'ACTIVE' " +
            "AND p.shop.status = 'ACTIVE' " +
            "AND p.shop.owner.status = 'ACTIVE' " +
            "AND ownerRole.name = 'SELLER' " +
            "AND p.category.isActive = true " +
            "AND p.category.parent.isActive = true " +
            "ORDER BY p.createdAt DESC")
    List<Product> findPublicProductsByShopId(@Param("shopId") Long shopId);

    List<Product> findAllByCategoryIdAndStatus(Long categoryId, String status);

    boolean existsByNameAndShopId(String name, Long shopId);

    boolean existsBySlug(String slug);
    long countByShopIdAndStatusNot(Long shopId, String status);

    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "preOrderConfig"})
    Page<Product> findByStatus(String status, Pageable pageable);
    boolean existsByNameAndShopIdAndStatusNot(String name, Long shopId, String status);

    @Override
    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "preOrderConfig"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);


    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "preOrderConfig"})
    @Query("SELECT p FROM Product p JOIN p.shop.owner.roles ownerRole " +
            "WHERE p.status = 'ACTIVE' " +
            "AND p.shop.status = 'ACTIVE' " +
            "AND p.shop.owner.status = 'ACTIVE' " +
            "AND ownerRole.name = 'SELLER' " +
            "AND p.category.isActive = true " +
            "AND p.category.parent.isActive = true")
    Page<Product> findActiveProductsFromActiveShops(Pageable pageable);

    @Modifying
    @Query(value = """
            UPDATE products product
            SET sold_count = product.sold_count + order_item.quantity
            FROM order_items order_item
            JOIN product_variants variant ON variant.id = order_item.product_variant_id
            WHERE order_item.id = :orderItemId
              AND product.id = variant.product_id
            """, nativeQuery = true)
    int incrementSoldCountByOrderItemId(@Param("orderItemId") Long orderItemId);

    @Modifying
    @Query(value = """
            UPDATE products product
            SET failed_dispute_count = product.failed_dispute_count + 1
            FROM order_items order_item
            JOIN product_variants variant ON variant.id = order_item.product_variant_id
            WHERE order_item.id = :orderItemId
              AND product.id = variant.product_id
            """, nativeQuery = true)
    int incrementFailedDisputeCountByOrderItemId(@Param("orderItemId") Long orderItemId);

}
