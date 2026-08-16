package com.commercehub.backend.category.repository;

import com.commercehub.backend.category.entity.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    boolean existsByName(String name);
    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "children")
    List<Category> findAllByParentIsNullAndIsActiveTrueOrderBySortOrderAsc();

    List<Category> findAllByParentIdOrderBySortOrderAsc(Long parentId);

    boolean existsByParentId(Long parentId);

    @Query("select category.id from Category category " +
            "where category.parent.id = :parentId and category.isActive = true " +
            "order by category.sortOrder asc")
    List<Long> findActiveChildIds(@Param("parentId") Long parentId);
}
