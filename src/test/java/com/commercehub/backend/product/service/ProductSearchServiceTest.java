package com.commercehub.backend.product.service;

import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.product.dto.request.ProductFilterRequest;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.service.MediaUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSearchServiceTest {

    @Test
    void searchConvertsStoredR2ObjectKeyToPublicThumbnailUrl() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductReviewService reviewService = mock(ProductReviewService.class);
        R2StorageProperties storage = new R2StorageProperties();
        storage.setPublicBaseUrl("https://images.example.test/");
        ProductSearchService service = new ProductSearchService(
                productRepository,
                productMapper,
                mock(CategoryRepository.class),
                reviewService,
                new MediaUrlService(storage)
        );

        Product product = Product.builder()
                .id(41L)
                .thumbnailUrl("shops/7/products/2026/09/service.webp")
                .variants(new ArrayList<>())
                .build();
        ProductResponse mapped = ProductResponse.builder()
                .id(41L)
                .thumbnailUrl(product.getThumbnailUrl())
                .build();

        when(productRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(product)));
        when(reviewService.getRatingSummaries(List.of(41L))).thenReturn(Map.of());
        when(productMapper.toResponse(
                any(Product.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(Long.class)
        )).thenReturn(mapped);

        PageResponse<ProductResponse> result = service.searchProducts(
                new ProductFilterRequest(),
                0,
                12
        );

        assertThat(result.getData()).singleElement()
                .extracting(ProductResponse::getThumbnailUrl)
                .isEqualTo("https://images.example.test/shops/7/products/2026/09/service.webp");
    }
}
