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
import com.commercehub.backend.product.entity.ProductImage;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductImageRepository;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    private final ProductImageRepository imageRepository;
    private final ProductVariantRepository variantRepository;

    @Transactional
    public ProductResponse createProduct(Long userId, CreateProductRequest request) {

        Shop shop = shopRepository.findByOwnerId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        if (!"ACTIVE".equals(shop.getStatus())) {
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

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

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
            for (var variantRequest : request.getVariants()) {
                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .name(variantRequest.getName())
                        .durationDays(variantRequest.getDurationDays())
                        .price(variantRequest.getPrice())
                        .sortOrder(variantRequest.getSortOrder())
                        .status("ACTIVE")
                        .stockCount(0) // Mặc định kho tài sản số = 0
                        .build();
                product.getVariants().add(variant);
            }
        }

        if (request.getImageUrls() != null) {
            int order = 1;
            for (String url : request.getImageUrls()) {
                ProductImage image = ProductImage.builder()
                        .product(product)
                        .imageUrl(url)
                        .sortOrder(order++)
                        .build();
                product.getImages().add(image);
            }
        }

        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatusNot(slug, "DELETED")
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toDetailResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByShop(Long shopId) {
        return productRepository.findAllByShopIdAndStatusNot(shopId, "DELETED")
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

        if (request.getName() != null && !request.getName().trim().isEmpty()  && !product.getName().equals(request.getName())) {

            if (productRepository.existsByNameAndShopIdAndStatusNot(request.getName(), product.getShop().getId(), "DELETED")) {
                throw new AppException(ErrorCode.PRODUCT_ALREADY_EXISTS);
            }
            product.setName(request.getName());

            String baseSlug = SlugUtils.toSlug(request.getName());
            String generatedSlug = baseSlug + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
            while (productRepository.existsBySlug(generatedSlug)) {
                generatedSlug = baseSlug + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
            }
            product.setSlug(generatedSlug);
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            product.setCategory(category);
        }
        if (request.getShortDescription() != null) product.setShortDescription(request.getShortDescription());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            // Chỉ cho phép ACTIVE hoặc INACTIVE, không cho hồi sinh sản phẩm đã xóa
            List<String> allowedStatuses = List.of("ACTIVE", "INACTIVE");
            if (!allowedStatuses.contains(request.getStatus())) {
                throw new AppException(ErrorCode.INVALID_STATUS);
            }
            if ("DELETED".equals(product.getStatus())) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            product.setStatus(request.getStatus());
        }

        return mapToProductResponse(productRepository.save(product));

    }


    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllActiveProducts(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(validPage, validSize, org.springframework.data.domain.Sort.by("createdAt").descending());

        // ĐÃ FIX Bug #L1: Dùng query mới để filter cả shop.status = ACTIVE
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




}