package com.commercehub.backend.product.service;

import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.product.dto.request.ProductFilterRequest;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CategoryRepository categoryRepository;
    private final ProductReviewService productReviewService;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(ProductFilterRequest request, int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;

        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());

        List<Long> categoryIds = resolveCategoryIds(request.getCategoryId());

        Specification<Product> spec = ProductSpecification.filterProducts(
                request.getKeyword(),
                categoryIds,
                request.getShopId()
        );


        Page<Product> products = productRepository.findAll(spec, pageable);
        Map<Long, ProductReviewService.RatingSummary> ratings =
                productReviewService.getRatingSummaries(
                        products.getContent().stream().map(Product::getId).toList()
                );

        Page<ProductResponse> productPage = products.map(product -> {
            BigDecimal minPrice = (product.getVariants() != null && !product.getVariants().isEmpty())
                    ? product.getVariants().stream()
                    .filter(v -> "ACTIVE".equals(v.getStatus()))
                    .map(ProductVariant::getPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO)
                    : BigDecimal.ZERO;

            ProductReviewService.RatingSummary rating = ratings.getOrDefault(
                    product.getId(),
                    ProductReviewService.RatingSummary.unrated()
            );
            return productMapper.toResponse(
                    product,
                    minPrice,
                    rating.averageRating(),
                    rating.reviewCount()
            );
        });

        return PageResponse.of(productPage);

    }

    private List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (Boolean.FALSE.equals(category.getIsActive())
                || (category.getParent() != null
                && Boolean.FALSE.equals(category.getParent().getIsActive()))) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (category.getParent() != null) {
            return List.of(category.getId());
        }

        return categoryRepository.findActiveChildIds(category.getId());
    }
}
