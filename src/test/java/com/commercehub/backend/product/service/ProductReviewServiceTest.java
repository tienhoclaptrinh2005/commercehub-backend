package com.commercehub.backend.product.service;

import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.product.dto.request.CreateProductReviewRequest;
import com.commercehub.backend.product.dto.response.ProductReviewResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductReview;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductReviewRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReviewServiceTest {

    private ProductReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private ProductMapper productMapper;
    private OrderItemRepository orderItemRepository;
    private ShopRepository shopRepository;
    private ProductReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ProductReviewRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        productMapper = mock(ProductMapper.class);
        orderItemRepository = mock(OrderItemRepository.class);
        shopRepository = mock(ShopRepository.class);
        service = new ProductReviewService(
                reviewRepository,
                productRepository,
                userRepository,
                productMapper,
                orderItemRepository,
                shopRepository
        );
    }

    @Test
    void creatingVisibleReviewUpdatesShopRatingCacheExactlyOnce() {
        User buyer = User.builder().id(1L).build();
        Product product = product(10L, 20L, 2L);
        CreateProductReviewRequest request = new CreateProductReviewRequest();
        request.setProductId(10L);
        request.setOrderItemId(30L);
        request.setRating(4);
        request.setComment("Sản phẩm hoạt động tốt");
        ProductReviewResponse response = ProductReviewResponse.builder().id(40L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderItemRepository.isReviewableOrderItem(1L, 10L, 30L)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(10L, 1L)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(ProductReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(shopRepository.addVisibleRating(20L, 4)).thenReturn(1);
        when(productMapper.toReviewResponse(any(ProductReview.class))).thenReturn(response);

        ProductReviewResponse created = service.createReview(1L, request);

        assertThat(created).isSameAs(response);
        verify(shopRepository, times(1)).addVisibleRating(20L, 4);
    }

    @Test
    void hidingReviewUpdatesShopOnceAndRepeatedRequestIsIdempotent() {
        ProductReview review = ProductReview.builder()
                .id(40L)
                .product(product(10L, 20L, 2L))
                .user(User.builder().id(1L).build())
                .orderItemId(30L)
                .rating(4)
                .isVisible(true)
                .build();
        ProductReviewResponse response = ProductReviewResponse.builder()
                .id(40L)
                .isVisible(false)
                .build();

        when(reviewRepository.findByIdForVisibilityUpdate(40L)).thenReturn(Optional.of(review));
        when(reviewRepository.saveAndFlush(review)).thenReturn(review);
        when(shopRepository.removeVisibleRating(20L, 4)).thenReturn(1);
        when(productMapper.toReviewResponse(review)).thenReturn(response);

        ProductReviewResponse hidden = service.setReviewVisibility(40L, false);
        ProductReviewResponse repeated = service.setReviewVisibility(40L, false);

        assertThat(hidden).isSameAs(response);
        assertThat(repeated).isSameAs(response);
        assertThat(review.getIsVisible()).isFalse();
        verify(reviewRepository, times(1)).saveAndFlush(review);
        verify(shopRepository, times(1)).removeVisibleRating(20L, 4);
    }

    private Product product(Long productId, Long shopId, Long ownerId) {
        User owner = User.builder().id(ownerId).build();
        Shop shop = Shop.builder().id(shopId).owner(owner).build();
        return Product.builder().id(productId).shop(shop).build();
    }
}
