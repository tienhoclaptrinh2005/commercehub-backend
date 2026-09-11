package com.commercehub.backend.product.service;

import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.product.dto.request.UpdateProductRequest;
import com.commercehub.backend.product.dto.request.UpdateVariantRequest;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceSellerEditTest {

    @Test
    void updatesAndAddsVariantsWhileDeactivatingRemovedHistoricalVariant() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductMapper mapper = mock(ProductMapper.class);
        ProductReviewService reviews = mock(ProductReviewService.class);
        ProductService service = new ProductService(
                productRepository,
                mock(ShopRepository.class),
                mock(CategoryRepository.class),
                mapper,
                reviews,
                new MediaUrlService(new R2StorageProperties()),
                mock(org.springframework.context.ApplicationEventPublisher.class)
        );
        Role role = new Role();
        role.setName("SELLER");
        User owner = User.builder().id(7L).status("ACTIVE")
                .roles(new HashSet<>(Set.of(role))).build();
        Shop shop = Shop.builder().id(3L).status("ACTIVE").owner(owner).build();
        Product product = Product.builder().id(5L).shop(shop).status("ACTIVE")
                .variants(new ArrayList<>()).build();
        ProductVariant retained = ProductVariant.builder().id(11L).product(product)
                .name("Cũ A").price(BigDecimal.TEN).status("ACTIVE").stockCount(2).build();
        ProductVariant removed = ProductVariant.builder().id(12L).product(product)
                .name("Cũ B").price(BigDecimal.ONE).status("ACTIVE").stockCount(0).build();
        product.getVariants().addAll(List.of(retained, removed));

        UpdateVariantRequest retainedRequest = new UpdateVariantRequest();
        retainedRequest.setId(11L);
        retainedRequest.setName("Gói A");
        retainedRequest.setPrice(BigDecimal.valueOf(20_000));
        retainedRequest.setStatus("ACTIVE");
        UpdateVariantRequest newRequest = new UpdateVariantRequest();
        newRequest.setName("Gói mới");
        newRequest.setPrice(BigDecimal.valueOf(30_000));
        newRequest.setStatus("ACTIVE");
        UpdateProductRequest request = new UpdateProductRequest();
        request.setVariants(List.of(retainedRequest, newRequest));

        when(productRepository.findSellerOwnedProductById(7L, 5L)).thenReturn(Optional.of(product));
        doNothing().when(mapper).updateProductFromRequest(request, product);
        when(productRepository.saveAndFlush(product)).thenReturn(product);
        when(reviews.getRatingSummary(5L)).thenReturn(ProductReviewService.RatingSummary.unrated());
        when(mapper.toSellerResponse(any(), any(), any(), anyLong()))
                .thenReturn(ProductResponse.builder().id(5L).build());

        ProductResponse response = service.updateProduct(7L, 5L, request);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(retained.getName()).isEqualTo("Gói A");
        assertThat(retained.getStockCount()).isEqualTo(2);
        assertThat(removed.getStatus()).isEqualTo("INACTIVE");
        assertThat(product.getVariants()).hasSize(3);
        ProductVariant created = product.getVariants().get(2);
        assertThat(created.getName()).isEqualTo("Gói mới");
        assertThat(created.getStockCount()).isZero();
    }
}
