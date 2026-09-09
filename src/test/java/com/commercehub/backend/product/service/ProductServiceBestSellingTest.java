package com.commercehub.backend.product.service;

import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.service.MediaUrlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceBestSellingTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductReviewService productReviewService = mock(ProductReviewService.class);
    private final ProductService productService = new ProductService(
            productRepository,
            shopRepository,
            categoryRepository,
            productMapper,
            productReviewService,
            new MediaUrlService(new R2StorageProperties()),
            mock(org.springframework.context.ApplicationEventPublisher.class)
    );

    @Test
    void returnsRealSoldCountRankingWithoutRequestingACountPage() {
        Product first = Product.builder().id(10L).soldCount(120L).build();
        Product second = Product.builder().id(20L).soldCount(80L).build();
        ProductResponse firstResponse = ProductResponse.builder().id(10L).soldCount(120L).build();
        ProductResponse secondResponse = ProductResponse.builder().id(20L).soldCount(80L).build();
        ProductReviewService.RatingSummary firstRating =
                new ProductReviewService.RatingSummary(new BigDecimal("4.80"), 12L);
        ProductReviewService.RatingSummary secondRating =
                new ProductReviewService.RatingSummary(new BigDecimal("4.50"), 8L);

        when(productRepository.findBestSellingActiveProducts(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(productReviewService.getRatingSummaries(List.of(10L, 20L)))
                .thenReturn(Map.of(10L, firstRating, 20L, secondRating));
        when(productMapper.toResponse(first, BigDecimal.ZERO, firstRating.averageRating(), firstRating.reviewCount()))
                .thenReturn(firstResponse);
        when(productMapper.toResponse(second, BigDecimal.ZERO, secondRating.averageRating(), secondRating.reviewCount()))
                .thenReturn(secondResponse);

        List<ProductResponse> result = productService.getBestSellingProducts(4);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findBestSellingActiveProducts(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(4);
        assertThat(result).containsExactly(firstResponse, secondResponse);
    }

    @Test
    void capsPublicBestSellingLimitAtTwelve() {
        when(productRepository.findBestSellingActiveProducts(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());
        when(productReviewService.getRatingSummaries(List.of())).thenReturn(Map.of());

        productService.getBestSellingProducts(10_000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findBestSellingActiveProducts(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(12);
    }
}
