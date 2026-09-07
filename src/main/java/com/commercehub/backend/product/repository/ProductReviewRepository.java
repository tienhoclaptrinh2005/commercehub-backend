package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    @EntityGraph(attributePaths = {"user"})
    Page<ProductReview> findByProductIdAndIsVisibleTrue(Long productId, Pageable pageable);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"product", "product.shop", "user"})
    @Query("SELECT review FROM ProductReview review WHERE review.id = :reviewId")
    Optional<ProductReview> findByIdForVisibilityUpdate(@Param("reviewId") Long reviewId);

    @Query("""
            SELECT r.product.id AS productId,
                   AVG(r.rating) AS averageRating,
                   COUNT(r.id) AS reviewCount
            FROM ProductReview r
            WHERE r.product.id IN :productIds
              AND r.isVisible = true
            GROUP BY r.product.id
            """)
    List<ProductRatingAggregate> findVisibleRatingAggregatesByProductIds(
            @Param("productIds") Collection<Long> productIds
    );

    interface ProductRatingAggregate {
        Long getProductId();
        Double getAverageRating();
        Long getReviewCount();
    }
}
