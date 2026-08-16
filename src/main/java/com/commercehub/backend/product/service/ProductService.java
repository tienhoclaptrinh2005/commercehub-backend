package com.commercehub.backend.product.service;

import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SlugUtils;
import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.UpdateProductRequest;
import com.commercehub.backend.product.dto.response.ProductDetailResponse;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse createProduct(Long userId, CreateProductRequest request) {

        Shop shop = shopRepository.findByOwnerId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        if (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus())) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }

        long currentProductCount = productRepository.countByShopIdAndStatusNot(shop.getId(), "DELETED");
        int allowedProductCount = shop.getOwner().getUserLevel().getAllowedProductCount();

        if (currentProductCount >= allowedProductCount) {
            throw new AppException(ErrorCode.PRODUCT_LIMIT_REACHED);
        }

        if (productRepository.existsByNameAndShopIdAndStatusNot(request.getName(), shop.getId(), "DELETED")) {
            throw new AppException(ErrorCode.PRODUCT_ALREADY_EXISTS);
        }

        Category category = getSellableCategory(request.getCategoryId());

        Product product = productMapper.toEntity(request);
        product.setShop(shop);
        product.setCategory(category);

        String baseSlug = SlugUtils.toSlug(request.getName());
        String generatedSlug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);

        while (productRepository.existsBySlug(generatedSlug)) {
            generatedSlug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        product.setSlug(generatedSlug);

        if (request.getVariants() != null) {
            Set<String> variantNames = new HashSet<>();
            for (var variantRequest : request.getVariants()) {
                String variantName = variantRequest.getName().trim();
                if (!variantNames.add(variantName.toLowerCase(Locale.ROOT))) {
                    throw new AppException(ErrorCode.VARIANT_ALREADY_EXISTS);
                }

                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .name(variantName)
                        .durationDays(variantRequest.getDurationDays())
                        .price(variantRequest.getPrice())
                        .sortOrder(variantRequest.getSortOrder())
                        .status("ACTIVE")
                        .stockCount(0)
                        .build();
                product.getVariants().add(variant);
            }
        }

        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findPublicBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toDetailResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByShop(Long shopId) {
        return productRepository.findPublicProductsByShopId(shopId)
                .stream()
                .map(this::mapToProductResponse)
                .toList();
    }

    @Transactional
    public void deleteProduct(Long userId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getShop().getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        validateShopCanSell(product.getShop());

        product.setStatus("DELETED");
        productRepository.save(product);
    }

    // (DÀNH CHO SELLER)
    @Transactional
    public ProductResponse updateProduct(Long userId, Long productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getShop().getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        validateShopCanSell(product.getShop());

        if ("DELETED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (request.getStatus() != null) {
            List<String> allowedStatuses = List.of("ACTIVE", "INACTIVE");
            if (!allowedStatuses.contains(request.getStatus())) {
                throw new AppException(ErrorCode.INVALID_STATUS);
            }
        }

        if (request.getName() != null && !request.getName().trim().isEmpty() && !product.getName().equals(request.getName())) {
            if (productRepository.existsByNameAndShopIdAndStatusNot(request.getName(), product.getShop().getId(), "DELETED")) {
                throw new AppException(ErrorCode.PRODUCT_ALREADY_EXISTS);
            }

            String baseSlug = SlugUtils.toSlug(request.getName());
            String generatedSlug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
            while (productRepository.existsBySlug(generatedSlug)) {
                generatedSlug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
            }
            product.setSlug(generatedSlug);
        }

        if (request.getCategoryId() != null && (product.getCategory() == null || !request.getCategoryId().equals(product.getCategory().getId()))) {
            Category category = getSellableCategory(request.getCategoryId());
            product.setCategory(category);
        }

        productMapper.updateProductFromRequest(request, product);

        return mapToProductResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllActiveProducts(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(validPage, validSize, org.springframework.data.domain.Sort.by("createdAt").descending());

        org.springframework.data.domain.Page<ProductResponse> productPage =
                productRepository.findActiveProductsFromActiveShops(pageable).map(this::mapToProductResponse);
        return PageResponse.of(productPage);
    }

    private ProductResponse mapToProductResponse(Product product) {
        BigDecimal minPrice = (product.getVariants() != null && !product.getVariants().isEmpty())
                ? product.getVariants().stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()))
                .map(ProductVariant::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        return productMapper.toResponse(product, minPrice);
    }

    private Category getSellableCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (Boolean.FALSE.equals(category.getIsActive())
                || (category.getParent() != null
                && Boolean.FALSE.equals(category.getParent().getIsActive()))) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (category.getParent() == null) {
            throw new AppException(ErrorCode.CATEGORY_MUST_BE_LEAF);
        }

        return category;
    }

    private void validateShopCanSell(Shop shop) {
        if (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus())) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }
    }
}
