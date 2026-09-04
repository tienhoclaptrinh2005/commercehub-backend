package com.commercehub.backend.shop.mapper;

import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class ShopMapperTest {

    private final ShopMapper mapper = Mappers.getMapper(ShopMapper.class);

    @Test
    void publicShopAvatarUsesOwnerAccountAvatar() {
        User owner = User.builder()
                .id(1L)
                .avatarUrl("https://cdn.example.com/user-avatar.png")
                .build();
        Shop shop = Shop.builder()
                .id(10L)
                .owner(owner)
                .name("Gian hàng chính thức")
                .shopAvatarUrl("https://cdn.example.com/obsolete-shop-avatar.png")
                .build();

        var response = mapper.toResponse(shop);

        assertThat(response.getShopAvatarUrl())
                .isEqualTo("https://cdn.example.com/user-avatar.png");
    }
}
