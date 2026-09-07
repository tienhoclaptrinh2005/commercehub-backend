package com.commercehub.backend.shop.repository;


import com.commercehub.backend.shop.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    @EntityGraph(attributePaths = {"owner"})
    Optional<Shop> findBySlug(String slug);
    boolean existsByName(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySlug(String slug);

    Optional<Shop> findByOwnerId(Long ownerId);

    List<Shop> findAllByOwnerId(Long ownerId);


    @EntityGraph(attributePaths = {"owner"})
    Page<Shop> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    @Query(value = """
            SELECT DISTINCT s
            FROM Shop s
            JOIN s.owner.roles ownerRole
            WHERE s.status = 'ACTIVE'
              AND s.owner.status = 'ACTIVE'
              AND ownerRole.name = 'SELLER'
              AND (
                    :keyword = ''
                    OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.owner.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
              AND (
                    :categoryId IS NULL
                    OR EXISTS (
                        SELECT product.id
                        FROM Product product
                        WHERE product.shop = s
                          AND product.status = 'ACTIVE'
                          AND product.category.isActive = true
                          AND (
                                product.category.parent IS NULL
                                OR product.category.parent.isActive = true
                          )
                          AND (
                                product.category.id = :categoryId
                                OR product.category.parent.id = :categoryId
                          )
                    )
              )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT s.id)
            FROM Shop s
            JOIN s.owner.roles ownerRole
            WHERE s.status = 'ACTIVE'
              AND s.owner.status = 'ACTIVE'
              AND ownerRole.name = 'SELLER'
              AND (
                    :keyword = ''
                    OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(s.owner.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
              AND (
                    :categoryId IS NULL
                    OR EXISTS (
                        SELECT product.id
                        FROM Product product
                        WHERE product.shop = s
                          AND product.status = 'ACTIVE'
                          AND product.category.isActive = true
                          AND (
                                product.category.parent IS NULL
                                OR product.category.parent.isActive = true
                          )
                          AND (
                                product.category.id = :categoryId
                                OR product.category.parent.id = :categoryId
                          )
                    )
              )
            """)
    Page<Shop> findAllPublicActive(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            UPDATE shops
            SET rating_avg = ROUND(
                        CAST((COALESCE(rating_sum, 0) + :rating) AS NUMERIC)
                        / (COALESCE(rating_count, 0) + 1),
                        2
                    ),
                rating_sum = COALESCE(rating_sum, 0) + :rating,
                rating_count = COALESCE(rating_count, 0) + 1,
                updated_at = NOW()
            WHERE id = :shopId
            """, nativeQuery = true)
    int addVisibleRating(
            @Param("shopId") Long shopId,
            @Param("rating") int rating
    );

    @Modifying
    @Query(value = """
            UPDATE shops
            SET rating_avg = CASE
                    WHEN COALESCE(rating_count, 0) <= 1 THEN 0
                    ELSE ROUND(
                        CAST(GREATEST(COALESCE(rating_sum, 0) - :rating, 0) AS NUMERIC)
                        / (rating_count - 1),
                        2
                    )
                END,
                rating_sum = GREATEST(COALESCE(rating_sum, 0) - :rating, 0),
                rating_count = GREATEST(COALESCE(rating_count, 0) - 1, 0),
                updated_at = NOW()
            WHERE id = :shopId
            """, nativeQuery = true)
    int removeVisibleRating(
            @Param("shopId") Long shopId,
            @Param("rating") int rating
    );



    @Override
    @EntityGraph(attributePaths = {"owner"})
    Page<Shop> findAll(Pageable pageable);
}
