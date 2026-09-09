package com.commercehub.backend.product.service;

import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.CreateVariantRequest;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.entity.LevelConfig;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.service.MediaUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceCreateTest {

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

    private Shop shop;
    private Category leafCategory;

    @BeforeEach
    void setUp() {
        Role sellerRole = new Role();
        sellerRole.setName("SELLER");
        User owner = User.builder()
                .id(7L)
                .status("ACTIVE")
                .roles(new HashSet<>(Set.of(sellerRole)))
                .userLevel(LevelConfig.builder().level(1).allowedProductCount(10).build())
                .build();
        shop = Shop.builder().id(3L).owner(owner).status("ACTIVE").build();
        Category parent = Category.builder().id(1L).name("Tài nguyên số").isActive(true).build();
        leafCategory = Category.builder()
                .id(2L)
                .name("Tài khoản")
                .isActive(true)
                .parent(parent)
                .build();

        when(shopRepository.findByOwnerId(7L)).thenReturn(Optional.of(shop));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(leafCategory));
        when(productRepository.countByShopIdAndStatusNot(3L, "DELETED")).thenReturn(0L);
        when(productRepository.existsByNameAndShopIdAndStatusNot(any(), anyLong(), any())).thenReturn(false);
    }

    @Test
    void createsDefaultTwentyFourHourConfigForPreOrderInSameProductGraph() {
        CreateProductRequest request = validRequest("PRE_ORDER", 1);
        Product mapped = Product.builder().id(40L).variants(new ArrayList<>()).build();
        ProductResponse response = ProductResponse.builder().id(40L).build();
        when(productMapper.toEntity(request)).thenReturn(mapped);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productReviewService.getRatingSummary(40L))
                .thenReturn(ProductReviewService.RatingSummary.unrated());
        when(productMapper.toResponse(any(), any(), any(), any())).thenReturn(response);

        ProductResponse result = productService.createProduct(7L, request);

        assertThat(result).isSameAs(response);
        assertThat(mapped.getPreOrderConfig()).isNotNull();
        assertThat(mapped.getPreOrderConfig().getProduct()).isSameAs(mapped);
        assertThat(mapped.getPreOrderConfig().getMaxProcessingHours()).isEqualTo(24);
        assertThat(mapped.getPreOrderConfig().getAutoRejectIfUnavailable()).isFalse();
        verify(productRepository).save(mapped);
    }

    @Test
    void rejectsMoreThanFiveVariantsBeforePersistingProduct() {
        CreateProductRequest request = validRequest("INSTANT", 6);

        assertThatThrownBy(() -> productService.createProduct(7L, request))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_VARIANT_LIMIT_REACHED));

        verify(productRepository, never()).save(any());
        verify(productMapper, never()).toEntity(any());
    }

    private CreateProductRequest validRequest(String deliveryType, int variantCount) {
        CreateProductRequest request = new CreateProductRequest();
        request.setCategoryId(2L);
        request.setName("Sản phẩm mới");
        request.setShortDescription("Mô tả ngắn");
        request.setDescription("Mô tả chi tiết");
        request.setProductType("ACCOUNT");
        request.setDeliveryType(deliveryType);
        request.setVariants(java.util.stream.IntStream.rangeClosed(1, variantCount)
                .mapToObj(index -> {
                    CreateVariantRequest variant = new CreateVariantRequest();
                    variant.setName("Biến thể " + index);
                    variant.setPrice(BigDecimal.valueOf(index * 10_000L));
                    variant.setSortOrder(index - 1);
                    return variant;
                })
                .toList());
        return request;
    }
}
