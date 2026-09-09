package com.commercehub.backend.product.service;

import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.product.dto.response.SellerProductListItemResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.service.MediaUrlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceSellerListTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductService productService = new ProductService(
            productRepository,
            mock(ShopRepository.class),
            mock(CategoryRepository.class),
            mock(ProductMapper.class),
            mock(ProductReviewService.class),
            new MediaUrlService(new R2StorageProperties())
    );

    @Test
    void returnsOnlyRepositoryScopedSellerProductsWithRealInventoryStats() {
        Category category = Category.builder().id(8L).name("Email & Cloud").build();
        Product product = Product.builder()
                .id(41L)
                .category(category)
                .name("Gmail cổ")
                .slug("gmail-co-abc123")
                .productType("ACCOUNT")
                .deliveryType("INSTANT")
                .status("ACTIVE")
                .soldCount(12L)
                .thumbnailUrl("https://example.com/gmail.png")
                .createdAt(OffsetDateTime.parse("2026-09-01T08:00:00+07:00"))
                .updatedAt(OffsetDateTime.parse("2026-09-02T08:00:00+07:00"))
                .build();
        Pageable pageRequest = PageRequest.of(0, 10);
        when(productRepository.findSellerProducts(
                eq(7L), eq("gmail"), eq(8L), eq("INSTANT"), eq("ACTIVE"), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(product), pageRequest, 1));

        ProductRepository.SellerProductInventoryStats stats =
                mock(ProductRepository.SellerProductInventoryStats.class);
        when(stats.getProductId()).thenReturn(41L);
        when(stats.getMinPrice()).thenReturn(new BigDecimal("50000.00"));
        when(stats.getStockCount()).thenReturn(9L);
        when(productRepository.findActiveVariantStats(List.of(41L))).thenReturn(List.of(stats));

        var result = productService.getSellerProducts(
                7L, "  gmail  ", 8L, "instant", "active", 0, 10
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        SellerProductListItemResponse response = result.getData().getFirst();
        assertThat(response.id()).isEqualTo(41L);
        assertThat(response.categoryName()).isEqualTo("Email & Cloud");
        assertThat(response.minPrice()).isEqualByComparingTo("50000.00");
        assertThat(response.stockCount()).isEqualTo(9L);
        assertThat(response.soldCount()).isEqualTo(12L);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findSellerProducts(
                eq(7L), eq("gmail"), eq(8L), eq("INSTANT"), eq("ACTIVE"), pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidFiltersBeforeQueryingDatabase() {
        assertThatThrownBy(() -> productService.getSellerProducts(
                7L, null, null, "UNKNOWN", null, 0, 10
        )).isInstanceOf(AppException.class);

        verify(productRepository, never()).findSellerProducts(
                any(), any(), any(), any(), any(), any(Pageable.class)
        );
    }
}
