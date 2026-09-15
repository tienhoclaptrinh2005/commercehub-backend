package com.commercehub.backend.category.service;

import com.commercehub.backend.category.dto.request.CreateCategoryRequest;
import com.commercehub.backend.category.dto.request.UpdateCategoryRequest;
import com.commercehub.backend.category.dto.response.CategoryResponse;
import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.category.mapper.CategoryMapper;
import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.cache.CacheNames;
import com.commercehub.backend.common.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    // Lấy toàn bộ danh mục đang hoạt động và xếp đúng vị trí để đưa lên Giao diện trang chủ
    @Cacheable(cacheNames = CacheNames.ACTIVE_CATEGORIES, key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {
        return categoryRepository.findAllByParentIsNullAndIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toHierarchyResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!isVisible(category)) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return toHierarchyResponse(category);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ACTIVE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true)
    })
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }

        Category category = categoryMapper.toEntity(request);
        String generatedSlug = SlugUtils.toSlug(request.getName());

        if (categoryRepository.existsBySlug(generatedSlug)) {
            throw new AppException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }
        category.setSlug(generatedSlug);
        category.setIsActive(true);
        category.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        category.setParent(resolveParent(request.getParentId(), null));

        return toHierarchyResponse(categoryRepository.save(category));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ACTIVE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true)
    })
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        categoryMapper.updateEntityFromRequest(request, category);

        // Xử lý name/slug SAU mapper — đảm bảo không bị đè
        if (request.getName() != null && !request.getName().equals(category.getName())) {

            if (categoryRepository.existsByName(request.getName())) {
                throw new AppException(ErrorCode.CATEGORY_ALREADY_EXISTS);
            }

            String newSlug = SlugUtils.toSlug(request.getName());
            if (categoryRepository.existsBySlug(newSlug)) {
                throw new AppException(ErrorCode.CATEGORY_ALREADY_EXISTS);
            }

            category.setName(request.getName());
            category.setSlug(newSlug);
        }

        if (request.getParentId() != null) {
            category.setParent(resolveParent(request.getParentId(), category.getId()));
        }

        if (Boolean.TRUE.equals(category.getIsActive())
                && category.getParent() != null
                && Boolean.FALSE.equals(category.getParent().getIsActive())) {
            throw new AppException(ErrorCode.CATEGORY_PARENT_INVALID);
        }

        return toHierarchyResponse(categoryRepository.save(category));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ACTIVE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true)
    })
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (category.getParent() == null) {
            List<Category> children = categoryRepository.findAllByParentIdOrderBySortOrderAsc(id);
            children.forEach(child -> child.setIsActive(false));
            categoryRepository.saveAll(children);
        }

        category.setIsActive(false);
        categoryRepository.save(category);
    }

    private Category resolveParent(Long parentId, Long currentCategoryId) {
        if (parentId == null) {
            return null;
        }

        if (parentId.equals(currentCategoryId)) {
            throw new AppException(ErrorCode.CATEGORY_PARENT_INVALID);
        }

        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (Boolean.FALSE.equals(parent.getIsActive()) || parent.getParent() != null) {
            throw new AppException(ErrorCode.CATEGORY_PARENT_INVALID);
        }

        if (currentCategoryId != null && categoryRepository.existsByParentId(currentCategoryId)) {
            throw new AppException(ErrorCode.CATEGORY_PARENT_INVALID);
        }

        return parent;
    }

    private boolean isVisible(Category category) {
        return Boolean.TRUE.equals(category.getIsActive())
                && (category.getParent() == null
                || Boolean.TRUE.equals(category.getParent().getIsActive()));
    }

    private CategoryResponse toHierarchyResponse(Category category) {
        CategoryResponse response = categoryMapper.toResponse(category);

        if (category.getParent() == null) {
            response.setChildren(category.getChildren().stream()
                    .filter(child -> Boolean.TRUE.equals(child.getIsActive()))
                    .sorted(Comparator.comparing(
                            child -> child.getSortOrder() == null ? 0 : child.getSortOrder()
                    ))
                    .map(this::toLeafResponse)
                    .toList());
        } else {
            response.setChildren(List.of());
        }

        return response;
    }

    private CategoryResponse toLeafResponse(Category category) {
        CategoryResponse response = categoryMapper.toResponse(category);
        response.setChildren(List.of());
        return response;
    }
}
