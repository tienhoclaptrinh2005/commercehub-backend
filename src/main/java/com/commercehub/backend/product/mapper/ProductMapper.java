package com.commercehub.backend.product.mapper;

import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.response.*;
import com.commercehub.backend.product.entity.PreOrderConfig;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductImage;
import com.commercehub.backend.product.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "soldCount", constant = "0L")
    @Mapping(target = "failedDisputeCount", constant = "0L")
    Product toEntity(CreateProductRequest request);

    @Mapping(target = "productId", source = "product.id")
    ProductVariantResponse toVariantResponse(ProductVariant variant);

    ProductImageResponse toImageResponse(ProductImage image);

    @Mapping(target = "productId", source = "product.id")
    PreOrderConfigResponse toPreOrderConfigResponse(PreOrderConfig config);


    @Mapping(target = "shopId", expression = "java(getShopId(product))")
    @Mapping(target = "shopName", expression = "java(getShopName(product))")
    @Mapping(target = "categoryId", expression = "java(getCategoryId(product))")
    @Mapping(target = "stockCount", expression = "java(calculateTotalStock(product.getVariants()))")
    ProductResponse toResponse(Product product, BigDecimal minPrice);


    @Mapping(target = "shopId", expression = "java(getShopId(product))")
    @Mapping(target = "shopName", expression = "java(getShopName(product))")
    @Mapping(target = "categoryId", expression = "java(getCategoryId(product))")
    @Mapping(target = "categoryName", expression = "java(getCategoryName(product))")
    @Mapping(target = "stockCount", expression = "java(calculateTotalStock(product.getVariants()))")
    @Mapping(target = "imageUrls", expression = "java(mapImages(product.getImages()))")
    ProductDetailResponse toDetailResponse(Product product);



    default Long getShopId(Product product) {
        return (product != null && product.getShop() != null) ? product.getShop().getId() : null;
    }

    default String getShopName(Product product) {
        return (product != null && product.getShop() != null) ? product.getShop().getName() : null;
    }

    default Long getCategoryId(Product product) {
        return (product != null && product.getCategory() != null) ? product.getCategory().getId() : null;
    }

    default String getCategoryName(Product product) {
        return (product != null && product.getCategory() != null) ? product.getCategory().getName() : null;
    }

    default List<String> mapImages(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        return images.stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());
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
}