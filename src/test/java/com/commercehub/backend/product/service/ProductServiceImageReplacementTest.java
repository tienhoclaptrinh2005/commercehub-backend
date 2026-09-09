package com.commercehub.backend.product.service;

import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.product.dto.request.UpdateProductRequest;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.event.ProductImageReplacedEvent;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImageReplacementTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductReviewService productReviewService = mock(ProductReviewService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProductService productService = new ProductService(
            productRepository,
            mock(ShopRepository.class),
            mock(CategoryRepository.class),
            productMapper,
            productReviewService,
            new MediaUrlService(new R2StorageProperties()),
            eventPublisher
    );

    @Test
    void publishesOldOwnedImageForCleanupOnlyAfterProductUpdate() {
        Role sellerRole = new Role();
        sellerRole.setName("SELLER");
        User owner = User.builder()
                .id(7L)
                .status("ACTIVE")
                .roles(new HashSet<>(Set.of(sellerRole)))
                .build();
        Shop shop = Shop.builder().id(3L).status("ACTIVE").owner(owner).build();
        Product product = Product.builder()
                .id(41L)
                .shop(shop)
                .status("ACTIVE")
                .thumbnailUrl("shops/3/products/2026/09/old.webp")
                .build();
        UpdateProductRequest request = new UpdateProductRequest();
        request.setThumbnailUrl("shops/3/products/2026/09/new.webp");
        ProductResponse response = ProductResponse.builder().build();

        when(productRepository.findById(41L)).thenReturn(Optional.of(product));
        doAnswer(invocation -> {
            UpdateProductRequest source = invocation.getArgument(0);
            Product target = invocation.getArgument(1);
            target.setThumbnailUrl(source.getThumbnailUrl());
            return null;
        }).when(productMapper).updateProductFromRequest(eq(request), eq(product));
        when(productRepository.save(product)).thenReturn(product);
        when(productReviewService.getRatingSummary(41L))
                .thenReturn(ProductReviewService.RatingSummary.unrated());
        when(productMapper.toResponse(
                eq(product),
                eq(BigDecimal.ZERO),
                any(BigDecimal.class),
                anyLong()
        )).thenReturn(response);

        productService.updateProduct(7L, 41L, request);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOfSatisfying(ProductImageReplacedEvent.class, event -> {
                    assertThat(event.shopId()).isEqualTo(3L);
                    assertThat(event.oldObjectKey())
                            .isEqualTo("shops/3/products/2026/09/old.webp");
                });
    }
}
