package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.Product; // Kiểm tra kỹ dòng import này!
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> , JpaSpecificationExecutor<Product> {
    interface ShopProductStats {
        Long getShopId();
        Long getActiveProductCount();
        Long getSoldProductCount();
    }

    interface SellerProductInventoryStats {
        Long getProductId();
        BigDecimal getMinPrice();
        Long getStockCount();
    }

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

    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "variants", "preOrderConfig"})
    @Query("SELECT p FROM Product p " +
            "WHERE p.id = :productId " +
            "AND p.shop.owner.id = :sellerId " +
            "AND p.status <> 'DELETED'")
    Optional<Product> findSellerOwnedProductById(
            @Param("sellerId") Long sellerId,
            @Param("productId") Long productId
    );

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
    long countByShopIdAndStatus(Long shopId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT product FROM Product product WHERE product.id = :productId")
    Optional<Product> findByIdForVariantUpdate(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"category"})
    @Query(value = """
            SELECT product
            FROM Product product
            JOIN product.shop shop
            WHERE shop.owner.id = :sellerId
              AND product.status <> 'DELETED'
              AND (
                    :keyword = ''
                    OR LOWER(product.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(COALESCE(product.shortDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
              AND (
                    :categoryId IS NULL
                    OR product.category.id = :categoryId
                    OR product.category.parent.id = :categoryId
              )
              AND (:deliveryType IS NULL OR product.deliveryType = :deliveryType)
              AND (:status IS NULL OR product.status = :status)
            """,
            countQuery = """
            SELECT COUNT(product)
            FROM Product product
            JOIN product.shop shop
            WHERE shop.owner.id = :sellerId
              AND product.status <> 'DELETED'
              AND (
                    :keyword = ''
                    OR LOWER(product.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(COALESCE(product.shortDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
              AND (
                    :categoryId IS NULL
                    OR product.category.id = :categoryId
                    OR product.category.parent.id = :categoryId
              )
              AND (:deliveryType IS NULL OR product.deliveryType = :deliveryType)
              AND (:status IS NULL OR product.status = :status)
            """)
    Page<Product> findSellerProducts(
            @Param("sellerId") Long sellerId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("deliveryType") String deliveryType,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
            SELECT variant.product.id AS productId,
                   MIN(variant.price) AS minPrice,
                   COALESCE(SUM(variant.stockCount), 0L) AS stockCount
            FROM ProductVariant variant
            WHERE variant.product.id IN :productIds
              AND variant.status = 'ACTIVE'
            GROUP BY variant.product.id
            """)
    List<SellerProductInventoryStats> findActiveVariantStats(
            @Param("productIds") List<Long> productIds
    );

    @EntityGraph(attributePaths = {"shop", "shop.owner", "category", "preOrderConfig"})
    Page<Product> findByStatus(String status, Pageable pageable);
    boolean existsByNameAndShopIdAndStatusNot(String name, Long shopId, String status);

    @Query("SELECT CASE WHEN COUNT(product) > 0 THEN true ELSE false END " +
            "FROM Product product " +
            "WHERE product.shop.id = :shopId " +
            "AND product.id <> :productId " +
            "AND product.status <> 'DELETED' " +
            "AND LOWER(TRIM(product.name)) = LOWER(TRIM(:name))")
    boolean existsSellerProductNameExcludingId(
            @Param("shopId") Long shopId,
            @Param("productId") Long productId,
            @Param("name") String name
    );

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

    @EntityGraph(attributePaths = {"shop", "shop.owner", "category"})
    @Query("SELECT p FROM Product p JOIN p.shop.owner.roles ownerRole " +
            "WHERE p.status = 'ACTIVE' " +
            "AND p.soldCount > 0 " +
            "AND p.shop.status = 'ACTIVE' " +
            "AND p.shop.owner.status = 'ACTIVE' " +
            "AND ownerRole.name = 'SELLER' " +
            "AND p.category.isActive = true " +
            "AND p.category.parent.isActive = true " +
            "ORDER BY p.soldCount DESC, p.createdAt DESC, p.id DESC")
    List<Product> findBestSellingActiveProducts(Pageable pageable);

    @Query("""
            SELECT product.shop.id AS shopId,
                   COUNT(product.id) AS activeProductCount,
                   COALESCE(SUM(product.soldCount), 0) AS soldProductCount
            FROM Product product
            WHERE product.shop.id IN :shopIds
              AND product.status = 'ACTIVE'
              AND product.category.isActive = true
              AND (
                    product.category.parent IS NULL
                    OR product.category.parent.isActive = true
              )
            GROUP BY product.shop.id
            """)
    List<ShopProductStats> findPublicShopProductStats(@Param("shopIds") List<Long> shopIds);

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
