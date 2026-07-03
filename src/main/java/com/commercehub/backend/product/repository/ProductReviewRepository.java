package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {


    Page<ProductReview> findByProductId(Long productId, Pageable pageable);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    // Tính điểm đánh giá trung bình
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId")
    Double calculateAverageRating(@Param("productId") Long productId);
}