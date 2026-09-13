package com.commercehub.backend.product.service;

import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.category.repository.CategoryRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.policy.PreOrderPolicy;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SlugUtils;
import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.UpdateProductRequest;
import com.commercehub.backend.product.dto.request.UpdateVariantRequest;
import com.commercehub.backend.product.dto.response.ProductDetailResponse;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.dto.response.SellerProductListItemResponse;
import com.commercehub.backend.product.entity.PreOrderConfig;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.storage.event.ProductImageReplacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int DEFAULT_BEST_SELLING_LIMIT = 4;
    private static final int MAX_BEST_SELLING_LIMIT = 12;
    private static final int MAX_SELLER_PRODUCT_PAGE_SIZE = 50;
    private static final int MAX_SELLER_PRODUCT_PAGE = 100;
    private static final int MAX_SELLER_PRODUCT_KEYWORD_LENGTH = 100;
    private static final int MAX_PRODUCT_VARIANTS = 5;

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductReviewService productReviewService;
    private final MediaUrlService mediaUrlService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProductResponse createProduct(Long userId, CreateProductRequest request) {

        Shop shop = shopRepository.findByOwnerId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        if (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus())
                || !shop.getOwner().hasRole("SELLER")) {
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

        if (request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getVariants().size() > MAX_PRODUCT_VARIANTS) {
            throw new AppException(ErrorCode.PRODUCT_VARIANT_LIMIT_REACHED);
        }

        request.setThumbnailUrl(mediaUrlService.normalizeOwnedProductImageReference(
                request.getThumbnailUrl(),
                shop.getId()
        ));

        Product product = productMapper.toEntity(request);
        product.setShop(shop);
        product.setCategory(category);

        // Mọi sản phẩm đặt hàng luôn có cấu hình mặc định ngay trong cùng
        // transaction tạo sản phẩm. Nhờ vậy trang mua hàng không rơi vào trạng
        // thái đã mở bán nhưng lại thiếu thông tin thời gian xử lý.
        if ("PRE_ORDER".equals(request.getDeliveryType())) {
            PreOrderConfig preOrderConfig = PreOrderConfig.builder()
                    .product(product)
                    .maxProcessingHours(PreOrderPolicy.PROCESSING_HOURS)
                    .autoRejectIfUnavailable(false)
                    .build();
            product.setPreOrderConfig(preOrderConfig);
        }

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
        ProductReviewService.RatingSummary rating =
                productReviewService.getRatingSummary(product.getId());
        ProductDetailResponse response = productMapper.toDetailResponse(
                product,
                rating.averageRating(),
                rating.reviewCount()
        );
        response.setThumbnailUrl(mediaUrlService.toPublicUrl(product.getThumbnailUrl()));
        response.setSellerAvatarUrl(mediaUrlService.toPublicUrl(response.getSellerAvatarUrl()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByShop(Long shopId) {
        List<Product> products = productRepository.findPublicProductsByShopId(shopId);
        Map<Long, ProductReviewService.RatingSummary> ratings = loadRatingSummaries(products);

        return products
                .stream()
                .map(product -> mapToProductResponse(
                        product,
                        ratings.getOrDefault(
                                product.getId(),
                                ProductReviewService.RatingSummary.unrated()
                        )
                ))
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

        if ("BANNED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        product.setStatus("DELETED");
        productRepository.save(product);
    }

    // (DÀNH CHO SELLER)
    @Transactional(readOnly = true)
    public ProductResponse getSellerProduct(Long userId, Long productId) {
        Product product = productRepository.findSellerOwnedProductById(userId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        validateShopCanSell(product.getShop());
        return mapToSellerProductResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(Long userId, Long productId, UpdateProductRequest request) {
        Product product = productRepository.findSellerOwnedProductById(userId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        String previousThumbnail = product.getThumbnailUrl();
        validateShopCanSell(product.getShop());

        if ("DELETED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // BANNED là quyết định kiểm duyệt của admin, seller không được tự sửa
        // hoặc bật lại sản phẩm cho đến khi admin khôi phục.
        if ("BANNED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (request.getStatus() != null) {
            List<String> allowedStatuses = List.of("ACTIVE", "INACTIVE");
            if (!allowedStatuses.contains(request.getStatus())) {
                throw new AppException(ErrorCode.INVALID_STATUS);
            }
        }

        if (request.getName() != null) {
            String normalizedName = request.getName().trim();
            if (normalizedName.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            request.setName(normalizedName);
            if (!product.getName().equals(normalizedName)
                    && productRepository.existsSellerProductNameExcludingId(
                    product.getShop().getId(), product.getId(), normalizedName)) {
                throw new AppException(ErrorCode.PRODUCT_ALREADY_EXISTS);
            }

            if (!product.getName().equals(normalizedName)) {
                String baseSlug = SlugUtils.toSlug(normalizedName);
                String generatedSlug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
                while (productRepository.existsBySlug(generatedSlug)) {
                    generatedSlug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
                }
                product.setSlug(generatedSlug);
            }
        }
        if (request.getShortDescription() != null) {
            String value = request.getShortDescription().trim();
            if (value.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            request.setShortDescription(value);
        }
        if (request.getDescription() != null) {
            String value = request.getDescription().trim();
            if (value.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            request.setDescription(value);
        }

        if (request.getCategoryId() != null && (product.getCategory() == null || !request.getCategoryId().equals(product.getCategory().getId()))) {
            Category category = getSellableCategory(request.getCategoryId());
            product.setCategory(category);
        }

        if (request.getThumbnailUrl() != null) {
            request.setThumbnailUrl(mediaUrlService.normalizeOwnedProductImageReference(
                    request.getThumbnailUrl(),
                    product.getShop().getId()
            ));
        }

        productMapper.updateProductFromRequest(request, product);
        if (request.getVariants() != null) {
            synchronizeVariants(product, request.getVariants());
        }

        Product savedProduct;
        try {
            savedProduct = productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.VARIANT_ALREADY_EXISTS);
        }
        if (!Objects.equals(previousThumbnail, savedProduct.getThumbnailUrl())
                && mediaUrlService.isOwnedProductImageReference(
                        previousThumbnail,
                        savedProduct.getShop().getId()
                )) {
            eventPublisher.publishEvent(new ProductImageReplacedEvent(
                    savedProduct.getShop().getId(),
                    previousThumbnail
            ));
        }
        return mapToSellerProductResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllActiveProducts(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(validPage, validSize, org.springframework.data.domain.Sort.by("createdAt").descending());

        org.springframework.data.domain.Page<Product> products =
                productRepository.findActiveProductsFromActiveShops(pageable);
        Map<Long, ProductReviewService.RatingSummary> ratings =
                loadRatingSummaries(products.getContent());

        return PageResponse.of(products.map(product -> mapToProductResponse(
                product,
                ratings.getOrDefault(
                        product.getId(),
                        ProductReviewService.RatingSummary.unrated()
                )
        )));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getBestSellingProducts(int limit) {
        int validLimit = limit <= 0
                ? DEFAULT_BEST_SELLING_LIMIT
                : Math.min(limit, MAX_BEST_SELLING_LIMIT);

        List<Product> products = productRepository.findBestSellingActiveProducts(
                org.springframework.data.domain.PageRequest.of(0, validLimit)
        );
        Map<Long, ProductReviewService.RatingSummary> ratings = loadRatingSummaries(products);

        return products.stream()
                .map(product -> mapToProductResponse(
                        product,
                        ratings.getOrDefault(
                                product.getId(),
                                ProductReviewService.RatingSummary.unrated()
                        )
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SellerProductListItemResponse> getSellerProducts(
            Long sellerId,
            String keyword,
            Long categoryId,
            String deliveryType,
            String status,
            int page,
            int size
    ) {
        if (page < 0 || page > MAX_SELLER_PRODUCT_PAGE || size < 1 || size > MAX_SELLER_PRODUCT_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (categoryId != null && categoryId <= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedKeyword = normalizeOptionalText(keyword);
        if (normalizedKeyword != null && normalizedKeyword.length() > MAX_SELLER_PRODUCT_KEYWORD_LENGTH) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (normalizedKeyword == null) {
            normalizedKeyword = "";
        }
        String normalizedDeliveryType = normalizeFilterValue(
                deliveryType,
                Set.of("INSTANT", "PRE_ORDER")
        );
        String normalizedStatus = normalizeFilterValue(
                status,
                Set.of("ACTIVE", "INACTIVE")
        );

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page,
                size,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")
                )
        );
        org.springframework.data.domain.Page<Product> products = productRepository.findSellerProducts(
                sellerId,
                normalizedKeyword,
                categoryId,
                normalizedDeliveryType,
                normalizedStatus,
                pageable
        );

        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, ProductRepository.SellerProductInventoryStats> inventoryByProduct = productIds.isEmpty()
                ? Map.of()
                : productRepository.findActiveVariantStats(productIds).stream()
                .collect(Collectors.toMap(
                        ProductRepository.SellerProductInventoryStats::getProductId,
                        Function.identity()
                ));

        return PageResponse.of(products.map(product -> {
            ProductRepository.SellerProductInventoryStats inventory = inventoryByProduct.get(product.getId());
            return new SellerProductListItemResponse(
                    product.getId(),
                    product.getName(),
                    product.getSlug(),
                    product.getCategory().getId(),
                    product.getCategory().getName(),
                    product.getProductType(),
                    product.getDeliveryType(),
                    product.getStatus(),
                    product.getSoldCount() == null ? 0L : product.getSoldCount(),
                    mediaUrlService.toPublicUrl(product.getThumbnailUrl()),
                    inventory == null || inventory.getMinPrice() == null
                            ? BigDecimal.ZERO
                            : inventory.getMinPrice(),
                    inventory == null || inventory.getStockCount() == null
                            ? 0L
                            : inventory.getStockCount(),
                    product.getCreatedAt(),
                    product.getUpdatedAt()
            );
        }));
    }

    private ProductResponse mapToProductResponse(Product product) {
        return mapToProductResponse(
                product,
                productReviewService.getRatingSummary(product.getId())
        );
    }

    private ProductResponse mapToProductResponse(
            Product product,
            ProductReviewService.RatingSummary rating
    ) {
        BigDecimal minPrice = (product.getVariants() != null && !product.getVariants().isEmpty())
                ? product.getVariants().stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()))
                .map(ProductVariant::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        ProductResponse response = productMapper.toResponse(
                product,
                minPrice,
                rating.averageRating(),
                rating.reviewCount()
        );
        response.setThumbnailUrl(mediaUrlService.toPublicUrl(product.getThumbnailUrl()));
        response.setSellerAvatarUrl(mediaUrlService.toPublicUrl(response.getSellerAvatarUrl()));
        return response;
    }

    private ProductResponse mapToSellerProductResponse(Product product) {
        BigDecimal minPrice = product.getVariants().stream()
                .filter(variant -> "ACTIVE".equals(variant.getStatus()))
                .map(ProductVariant::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        ProductReviewService.RatingSummary rating = productReviewService.getRatingSummary(product.getId());
        ProductResponse response = productMapper.toSellerResponse(
                product,
                minPrice,
                rating.averageRating(),
                rating.reviewCount()
        );
        response.setThumbnailUrl(mediaUrlService.toPublicUrl(product.getThumbnailUrl()));
        response.setSellerAvatarUrl(mediaUrlService.toPublicUrl(response.getSellerAvatarUrl()));
        return response;
    }

    /**
     * Đồng bộ tối đa năm biến thể trong cùng transaction với thông tin sản phẩm.
     * Biến thể đã có lịch sử không bị xóa vật lý; khi seller bỏ khỏi form nó được
     * chuyển INACTIVE để khóa mua mới nhưng vẫn giữ nguyên FK của đơn cũ.
     */
    private void synchronizeVariants(Product product, List<UpdateVariantRequest> requests) {
        if (requests.isEmpty() || requests.size() > MAX_PRODUCT_VARIANTS) {
            throw new AppException(ErrorCode.PRODUCT_VARIANT_LIMIT_REACHED);
        }

        Map<Long, ProductVariant> existingById = product.getVariants().stream()
                .filter(variant -> variant.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        long newVariantCount = requests.stream().filter(request -> request.getId() == null).count();
        if (existingById.size() + newVariantCount > MAX_PRODUCT_VARIANTS) {
            throw new AppException(ErrorCode.PRODUCT_VARIANT_LIMIT_REACHED);
        }
        Set<Long> retainedIds = new HashSet<>();
        Set<String> normalizedNames = new HashSet<>();

        for (int index = 0; index < requests.size(); index++) {
            UpdateVariantRequest request = requests.get(index);
            if (request.getName() == null || request.getName().trim().isEmpty()
                    || request.getPrice() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            String name = request.getName().trim();
            if (!normalizedNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new AppException(ErrorCode.VARIANT_ALREADY_EXISTS);
            }
            String status = request.getStatus() == null
                    ? "ACTIVE"
                    : request.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
                throw new AppException(ErrorCode.VARIANT_INVALID_STATUS);
            }

            ProductVariant variant;
            if (request.getId() == null) {
                variant = ProductVariant.builder()
                        .product(product)
                        .stockCount(0)
                        .build();
                product.getVariants().add(variant);
            } else {
                variant = existingById.get(request.getId());
                if (variant == null || !retainedIds.add(request.getId())) {
                    throw new AppException(ErrorCode.INVALID_REQUEST);
                }
            }
            variant.setName(name);
            variant.setPrice(request.getPrice());
            variant.setDurationDays(request.getDurationDays());
            variant.setSortOrder(request.getSortOrder() == null ? index : request.getSortOrder());
            variant.setStatus(status);
        }

        existingById.forEach((id, variant) -> {
            if (!retainedIds.contains(id)) {
                variant.setStatus("INACTIVE");
            }
        });
    }

    private Map<Long, ProductReviewService.RatingSummary> loadRatingSummaries(
            List<Product> products
    ) {
        return productReviewService.getRatingSummaries(
                products.stream().map(Product::getId).toList()
        );
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeFilterValue(String value, Set<String> allowedValues) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
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
                || !"ACTIVE".equals(shop.getOwner().getStatus())
                || !shop.getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }
    }
}
