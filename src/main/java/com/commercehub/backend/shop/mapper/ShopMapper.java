package com.commercehub.backend.shop.mapper;

import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.dto.request.UpdateShopRequest;
import com.commercehub.backend.shop.dto.response.ShopResponse;
import com.commercehub.backend.shop.dto.response.ShopApplicationResponse;
import com.commercehub.backend.shop.entity.Shop;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Builder;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        builder = @Builder(disableBuilder = false))
public interface ShopMapper {

    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    Shop toEntity(CreateShopRequest request);

    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerName", source = "owner.fullName")
    @Mapping(target = "ownerUsername", source = "owner.username")
    @Mapping(target = "shopAvatarUrl", source = "owner.avatarUrl")
    ShopResponse toResponse(Shop shop);

    @Mapping(target = "ownerId", source = "shop.owner.id")
    @Mapping(target = "ownerName", source = "shop.owner.fullName")
    @Mapping(target = "ownerUsername", source = "shop.owner.username")
    @Mapping(target = "shopAvatarUrl", source = "shop.owner.avatarUrl")
    @Mapping(target = "ownerCompletedPurchaseCount", source = "ownerCompletedPurchaseCount")
    @Mapping(target = "successfulSaleCount", source = "successfulSaleCount")
    ShopResponse toResponse(
            Shop shop,
            long ownerCompletedPurchaseCount,
            long successfulSaleCount
    );

    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "username", source = "owner.username")
    ShopApplicationResponse toApplicationResponse(Shop shop);

    @Mapping(target = "name", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "shopAvatarUrl", ignore = true)

    void updateEntityFromRequest(UpdateShopRequest request, @MappingTarget Shop shop);
}
