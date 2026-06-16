package com.commercehub.backend.category.service;

import com.commercehub.backend.category.dto.request.CreateCategoryRequest;
import com.commercehub.backend.category.dto.request.UpdateCategoryRequest;
import com.commercehub.backend.category.dto.response.CategoryResponse;
import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.category.mapper.CategoryMapper;
import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    // Lấy toàn bộ danh mục đang hoạt động và xếp đúng vị trí để đưa lên Giao diện trang chủ
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {
        return categoryRepository.findAllByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (Boolean.FALSE.equals(category.getIsActive())) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return categoryMapper.toResponse(category);
    }

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

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

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

        categoryMapper.updateEntityFromRequest(request, category);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        category.setIsActive(false);
        categoryRepository.save(category);
    }
}