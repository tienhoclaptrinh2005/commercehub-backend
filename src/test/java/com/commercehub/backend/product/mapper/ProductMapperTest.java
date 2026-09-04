package com.commercehub.backend.product.mapper;

import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void productSellerAvatarComesFromShopInsteadOfOwnerAccount() {
        User owner = User.builder()
                .username("seller_user")
                .avatarUrl("https://cdn.example.com/user-avatar.png")
                .build();
        Shop shop = Shop.builder()
                .id(10L)
                .owner(owner)
                .name("Gian hàng chính thức")
                .shopAvatarUrl("https://cdn.example.com/shop-avatar.png")
                .build();
        Product product = Product.builder()
                .id(20L)
                .shop(shop)
                .name("Sản phẩm thật")
                .slug("san-pham-that")
                .build();

        var response = mapper.toResponse(
                product,
                BigDecimal.TEN,
                new BigDecimal("5.00"),
                0L
        );

        assertThat(response.getShopName()).isEqualTo("Gian hàng chính thức");
        assertThat(response.getSellerUsername()).isEqualTo("seller_user");
        assertThat(response.getSellerAvatarUrl())
                .isEqualTo("https://cdn.example.com/shop-avatar.png");
    }
}
