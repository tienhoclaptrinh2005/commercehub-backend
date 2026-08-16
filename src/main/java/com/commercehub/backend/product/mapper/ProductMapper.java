package com.commercehub.backend.product.mapper;

import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.UpdateProductRequest;
import com.commercehub.backend.product.dto.response.*;
import com.commercehub.backend.product.entity.*;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "soldCount", constant = "0L")
    @Mapping(target = "failedDisputeCount", constant = "0L")
    @Mapping(target = "variants", ignore = true)
    @Mapping(target = "preOrderConfig", ignore = true)
    Product toEntity(CreateProductRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "slug", ignore = true)
    void updateProductFromRequest(UpdateProductRequest request, @MappingTarget Product product);

    @Mapping(target = "productId", source = "product.id")
    ProductVariantResponse toVariantResponse(ProductVariant variant);

    @Mapping(target = "productId", source = "product.id")
    PreOrderConfigResponse toPreOrderConfigResponse(PreOrderConfig config);

    @Mapping(target = "shopId", expression = "java(getShopId(product))")
    @Mapping(target = "shopName", expression = "java(getShopName(product))")
    @Mapping(target = "sellerUsername", expression = "java(getSellerUsername(product))")
    @Mapping(target = "sellerAvatarUrl", expression = "java(getSellerAvatarUrl(product))")
    @Mapping(target = "categoryId", expression = "java(getCategoryId(product))")
    @Mapping(target = "categoryName", expression = "java(getCategoryName(product))")
    @Mapping(target = "stockCount", expression = "java(calculateTotalStock(product.getVariants()))")
    @Mapping(target = "variants", expression = "java(mapActiveVariants(product.getVariants()))")
    @Mapping(target = "averageRating", source = "averageRating")
    @Mapping(target = "reviewCount", source = "reviewCount")
    ProductResponse toResponse(
            Product product,
            BigDecimal minPrice,
            BigDecimal averageRating,
            Long reviewCount
    );

    @Mapping(target = "shopId", expression = "java(getShopId(product))")
    @Mapping(target = "shopName", expression = "java(getShopName(product))")
    @Mapping(target = "sellerUsername", expression = "java(getSellerUsername(product))")
    @Mapping(target = "sellerAvatarUrl", expression = "java(getSellerAvatarUrl(product))")
    @Mapping(target = "categoryId", expression = "java(getCategoryId(product))")
    @Mapping(target = "categoryName", expression = "java(getCategoryName(product))")
    @Mapping(target = "stockCount", expression = "java(calculateTotalStock(product.getVariants()))")
    @Mapping(target = "variants", expression = "java(mapActiveVariants(product.getVariants()))")
    @Mapping(target = "averageRating", source = "averageRating")
    @Mapping(target = "reviewCount", source = "reviewCount")
    ProductDetailResponse toDetailResponse(
            Product product,
            BigDecimal averageRating,
            Long reviewCount
    );

    @Mapping(target = "variantId", source = "productVariant.id")
    DigitalAssetResponse toDigitalAssetResponse(DigitalAsset asset);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "reviewerName", source = "user.fullName")
    @Mapping(target = "reviewerAvatar", source = "user.avatarUrl")
    ProductReviewResponse toReviewResponse(ProductReview review);

    default Long getShopId(Product product) {
        return (product != null && product.getShop() != null) ? product.getShop().getId() : null;
    }

    default String getShopName(Product product) {
        return (product != null && product.getShop() != null) ? product.getShop().getName() : null;
    }

    default String getSellerUsername(Product product) {
        return (product != null
                && product.getShop() != null
                && product.getShop().getOwner() != null)
                ? product.getShop().getOwner().getUsername()
                : null;
    }

    default String getSellerAvatarUrl(Product product) {
        return (product != null
                && product.getShop() != null
                && product.getShop().getOwner() != null)
                ? product.getShop().getOwner().getAvatarUrl()
                : null;
    }

    default Long getCategoryId(Product product) {
        return (product != null && product.getCategory() != null) ? product.getCategory().getId() : null;
    }

    default String getCategoryName(Product product) {
        return (product != null && product.getCategory() != null) ? product.getCategory().getName() : null;
    }

    default Integer calculateTotalStock(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return 0;
        }
        return variants.stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()))
                .mapToInt(ProductVariant::getStockCount)
                .sum();
    }

    default List<ProductVariantResponse> mapActiveVariants(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        return variants.stream()
                .filter(variant -> "ACTIVE".equals(variant.getStatus()))
                .sorted(Comparator.comparing(
                        ProductVariant::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(this::toVariantResponse)
                .toList();
    }
}
