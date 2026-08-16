package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> filterProducts(String keyword, List<Long> categoryIds, Long shopId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            predicates.add(cb.equal(root.get("shop").get("status"), "ACTIVE"));

            predicates.add(cb.equal(root.get("shop").get("owner").get("status"), "ACTIVE"));

            predicates.add(cb.isTrue(root.get("category").get("isActive")));

            predicates.add(cb.isTrue(root.get("category").get("parent").get("isActive")));

            if (keyword != null && !keyword.trim().isEmpty()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likeKeyword),
                        cb.like(cb.lower(root.get("shortDescription")), likeKeyword)
                ));
            }


            if (categoryIds != null) {
                predicates.add(categoryIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("category").get("id").in(categoryIds));
            }


            if (shopId != null) {
                predicates.add(cb.equal(root.get("shop").get("id"), shopId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
