package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.cache.CacheNames;
import com.commercehub.backend.product.dto.request.CreateVariantRequest;
import com.commercehub.backend.product.dto.request.UpdateVariantRequest;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private static final long MAX_PRODUCT_VARIANTS = 5L;

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true)
    })
    @Transactional
    public ProductVariant createVariant(Long sellerId, CreateVariantRequest request) {
        if (request.getProductId() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Product product = productRepository.findByIdForVariantUpdate(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validateSellerCanEdit(product, sellerId);

        if (variantRepository.countByProductId(product.getId()) >= MAX_PRODUCT_VARIANTS) {
            throw new AppException(ErrorCode.PRODUCT_VARIANT_LIMIT_REACHED);
        }

        String normalizedName = request.getName().trim();
        if (variantRepository.existsByNormalizedName(product.getId(), normalizedName)) {
            throw new AppException(ErrorCode.VARIANT_ALREADY_EXISTS);
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .name(normalizedName)
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .stockCount(0)
                .status("ACTIVE")
                .build();

        return saveVariant(variant);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true)
    })
    @Transactional
    public ProductVariant updateVariant(Long sellerId, Long variantId, UpdateVariantRequest request) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        validateSellerCanEdit(variant.getProduct(), sellerId);

        if (request.getName() != null) {
            String normalizedName = request.getName().trim();
            if (normalizedName.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            if (variantRepository.existsByNormalizedNameAndIdNot(
                    variant.getProduct().getId(), normalizedName, variant.getId())) {
                throw new AppException(ErrorCode.VARIANT_ALREADY_EXISTS);
            }
            variant.setName(normalizedName);
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
            String normalizedStatus = request.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!List.of("ACTIVE", "INACTIVE").contains(normalizedStatus)) {
                throw new AppException(ErrorCode.VARIANT_INVALID_STATUS);
            }
            variant.setStatus(normalizedStatus);
        }

        return saveVariant(variant);
    }
    @Transactional(readOnly = true)
    public List<ProductVariant> getVariantsByProduct(Long productId) {
        Product product = productRepository.findPublicById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        return product.getVariants().stream()
                .filter(variant -> "ACTIVE".equals(variant.getStatus()))
                .sorted(java.util.Comparator.comparing(ProductVariant::getSortOrder))
                .toList();
    }

    private void validateSellerCanEdit(Product product, Long sellerId) {
        if ("DELETED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!product.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(product.getShop().getStatus())
                || !"ACTIVE".equals(product.getShop().getOwner().getStatus())
                || !product.getShop().getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }
    }

    private ProductVariant saveVariant(ProductVariant variant) {
        try {
            // Flush ngay để bắt được cả trường hợp hai request đồng thời vượt qua bước kiểm tra tồn tại.
            return variantRepository.saveAndFlush(variant);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.VARIANT_ALREADY_EXISTS);
        }
    }
}
