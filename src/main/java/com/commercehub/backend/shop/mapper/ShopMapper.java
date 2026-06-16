package com.commercehub.backend.shop.mapper;

import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.dto.request.UpdateShopRequest;
import com.commercehub.backend.shop.dto.response.ShopResponse;
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

    Shop toEntity(CreateShopRequest request);

    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerName", source = "owner.fullName")
    ShopResponse toResponse(Shop shop);
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "slug", ignore = true)

    void updateEntityFromRequest(UpdateShopRequest request, @MappingTarget Shop shop);
}