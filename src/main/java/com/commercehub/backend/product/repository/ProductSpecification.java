package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> filterProducts(String keyword, Long categoryId, Long shopId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            // ĐÃ FIX Bug #NEW4: Ẩn sản phẩm từ shop bị BANNED/INACTIVE trên trang tìm kiếm
            predicates.add(cb.equal(root.get("shop").get("status"), "ACTIVE"));

            if (keyword != null && !keyword.trim().isEmpty()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likeKeyword),
                        cb.like(cb.lower(root.get("shortDescription")), likeKeyword)
                ));
            }


            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }


            if (shopId != null) {
                predicates.add(cb.equal(root.get("shop").get("id"), shopId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}