package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.product.dto.request.CreateVariantRequest;
import com.commercehub.backend.product.dto.request.UpdateVariantRequest;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ProductVariant createVariant(Long sellerId, CreateVariantRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if ("DELETED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (!product.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .name(request.getName())
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .stockCount(0)
                .status("ACTIVE")
                .build();

        return variantRepository.save(variant);
    }

    @Transactional
    public ProductVariant updateVariant(Long sellerId, Long variantId, UpdateVariantRequest request) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));


        if (!variant.getProduct().getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (request.getName() != null) {
            variant.setName(request.getName());
        }
        if (request.getPrice() != null) {
            variant.setPrice(request.getPrice());
        }
        if (request.getDurationDays() != null) {
            variant.setDurationDays(request.getDurationDays());
        }
        if (request.getSortOrder() != null) {
            variant.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            variant.setStatus(request.getStatus());
        }

        return variantRepository.save(variant);
    }
    @Transactional(readOnly = true)
    public List<ProductVariant> getVariantsByProduct(Long productId) {
        return variantRepository.findByProductIdAndStatusOrderBySortOrderAsc(productId, "ACTIVE");
    }
}