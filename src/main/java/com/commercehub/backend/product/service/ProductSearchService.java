package com.commercehub.backend.product.service;

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

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(ProductFilterRequest request, int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;

        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());

        Specification<Product> spec = ProductSpecification.filterProducts(
                request.getKeyword(),
                request.getCategoryId(),
                request.getShopId()
        );


        Page<ProductResponse> productPage = productRepository.findAll(spec,pageable).map(product -> {
                BigDecimal minPrice = (product.getVariants()!=null && !product.getVariants().isEmpty())
                    ?product.getVariants().stream()
                        .filter(v -> "ACTIVE".equals(v.getStatus()))
                        .map(ProductVariant::getPrice)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO)
                    :BigDecimal.ZERO;
            return productMapper.toResponse(product, minPrice);

        });

        return PageResponse.of(productPage);

    }
}