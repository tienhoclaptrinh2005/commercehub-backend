package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.cache.CacheNames;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.product.dto.request.CreateProductReviewRequest;
import com.commercehub.backend.product.dto.response.ProductReviewResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductReview;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductReviewRepository;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private static final BigDecimal DEFAULT_PRODUCT_RATING = new BigDecimal("5.00");

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductMapper productMapper;
    private final OrderItemRepository orderItemRepository;
    private final ShopRepository shopRepository;
    private final MediaUrlService mediaUrlService;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SHOPS, allEntries = true)
    })
    @Transactional
    public ProductReviewResponse createReview(Long userId, CreateProductReviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getShop().getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_REVIEW_OWN_PRODUCT);
        }

        if (!orderItemRepository.isReviewableOrderItem(
                userId,
                request.getProductId(),
                request.getOrderItemId())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_PURCHASED);
        }

        if (reviewRepository.existsByProductIdAndUserId(request.getProductId(), userId)) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        ProductReview review = ProductReview.builder()
                .product(product)
                .user(user)
                .orderItemId(request.getOrderItemId())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        ProductReview savedReview;
        try {
            savedReview = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        ensureShopRatingUpdated(
                shopRepository.addVisibleRating(product.getShop().getId(), request.getRating())
        );
        return resolveReviewerAvatar(productMapper.toReviewResponse(savedReview));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SHOPS, allEntries = true)
    })
    @Transactional
    public ProductReviewResponse setReviewVisibility(Long reviewId, boolean visible) {
        ProductReview review = reviewRepository.findByIdForVisibilityUpdate(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));

        boolean currentlyVisible = Boolean.TRUE.equals(review.getIsVisible());
        if (currentlyVisible == visible) {
            return resolveReviewerAvatar(productMapper.toReviewResponse(review));
        }

        review.setIsVisible(visible);
        ProductReview savedReview = reviewRepository.saveAndFlush(review);
        Long shopId = review.getProduct().getShop().getId();
        int updatedRows = visible
                ? shopRepository.addVisibleRating(shopId, review.getRating())
                : shopRepository.removeVisibleRating(shopId, review.getRating());
        ensureShopRatingUpdated(updatedRows);
        return resolveReviewerAvatar(productMapper.toReviewResponse(savedReview));
    }

    @Transactional(readOnly = true)
    public Page<ProductReviewResponse> getReviewsByProduct(Long productId, int page, int size) {
        productRepository.findPublicById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 10 : size;

        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());

        return reviewRepository.findByProductIdAndIsVisibleTrue(productId, pageable)
                .map(productMapper::toReviewResponse)
                .map(this::resolveReviewerAvatar);
    }

    @Transactional(readOnly = true)
    public RatingSummary getRatingSummary(Long productId) {
        return getRatingSummaries(List.of(productId))
                .getOrDefault(productId, RatingSummary.unrated());
    }

    @Transactional(readOnly = true)
    public Map<Long, RatingSummary> getRatingSummaries(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        List<Long> distinctProductIds = productIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (distinctProductIds.isEmpty()) {
            return Map.of();
        }

        return reviewRepository.findVisibleRatingAggregatesByProductIds(distinctProductIds)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        ProductReviewRepository.ProductRatingAggregate::getProductId,
                        aggregate -> new RatingSummary(
                                BigDecimal.valueOf(aggregate.getAverageRating())
                                        .setScale(2, RoundingMode.HALF_UP),
                                aggregate.getReviewCount()
                        )
                ));
    }

    public record RatingSummary(BigDecimal averageRating, long reviewCount) {
        public static RatingSummary unrated() {
            return new RatingSummary(DEFAULT_PRODUCT_RATING, 0L);
        }
    }

    private void ensureShopRatingUpdated(int updatedRows) {
        if (updatedRows != 1) {
            throw new AppException(ErrorCode.SHOP_NOT_FOUND);
        }
    }

    private ProductReviewResponse resolveReviewerAvatar(ProductReviewResponse response) {
        response.setReviewerAvatar(mediaUrlService.toPublicUrl(response.getReviewerAvatar()));
        return response;
    }
}
