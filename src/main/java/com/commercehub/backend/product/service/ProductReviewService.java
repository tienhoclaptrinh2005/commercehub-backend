package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.product.dto.request.CreateProductReviewRequest;
import com.commercehub.backend.product.dto.response.ProductReviewResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductReview;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductReviewRepository;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductMapper productMapper;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public ProductReviewResponse createReview(Long userId, CreateProductReviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getShop().getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_REVIEW_OWN_PRODUCT);
        }

        if (!orderItemRepository.existsByOrder_UserIdAndProductVariant_Product_IdAndOrder_Status(
                userId, request.getProductId(), "DELIVERED")) {
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

        ProductReview savedReview = reviewRepository.save(review);
        return productMapper.toReviewResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public Page<ProductReviewResponse> getReviewsByProduct(Long productId, int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 10 : size;

        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());

        return reviewRepository.findByProductId(productId, pageable).map(productMapper::toReviewResponse);
    }
}