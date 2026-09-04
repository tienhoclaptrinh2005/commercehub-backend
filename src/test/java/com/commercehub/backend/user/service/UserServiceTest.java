package com.commercehub.backend.user.service;

import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private UserMapper userMapper;
    private OrderStatisticsService orderStatisticsService;
    private ShopRepository shopRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userMapper = mock(UserMapper.class);
        orderStatisticsService = mock(OrderStatisticsService.class);
        shopRepository = mock(ShopRepository.class);
        userService = new UserService(
                userRepository,
                userMapper,
                orderStatisticsService,
                shopRepository
        );
    }

    @Test
    void publicSellerProfileUsesActiveShopIdentity() {
        User user = User.builder()
                .id(1L)
                .username("seller_user")
                .avatarUrl("https://cdn.example.com/user-avatar.png")
                .status("ACTIVE")
                .build();
        Shop shop = Shop.builder()
                .id(10L)
                .owner(user)
                .name("Gian hàng chính thức")
                .shopAvatarUrl("https://cdn.example.com/shop-avatar.png")
                .status("ACTIVE")
                .build();
        UserResponse mappedResponse = new UserResponse();

        when(userRepository.findByUsername("seller_user")).thenReturn(Optional.of(user));
        when(orderStatisticsService.countCompletedPurchases(1L)).thenReturn(2L);
        when(orderStatisticsService.countSuccessfulSalesByOwner(1L)).thenReturn(3L);
        when(userMapper.toUserResponse(user, 2L, 3L)).thenReturn(mappedResponse);
        when(shopRepository.findByOwnerId(1L)).thenReturn(Optional.of(shop));

        UserResponse response = userService.getUserByUsername("seller_user");

        assertThat(response.getShopId()).isEqualTo(10L);
        assertThat(response.getShopName()).isEqualTo("Gian hàng chính thức");
        assertThat(response.getShopAvatarUrl())
                .isEqualTo("https://cdn.example.com/user-avatar.png");
    }

    @Test
    void publicProfileDoesNotExposeBannedShopIdentity() {
        User user = User.builder()
                .id(1L)
                .username("seller_user")
                .status("ACTIVE")
                .build();
        Shop shop = Shop.builder()
                .id(10L)
                .owner(user)
                .name("Gian hàng bị khóa")
                .shopAvatarUrl("https://cdn.example.com/banned-shop.png")
                .status("BANNED")
                .build();
        UserResponse mappedResponse = new UserResponse();

        when(userRepository.findByUsername("seller_user")).thenReturn(Optional.of(user));
        when(userMapper.toUserResponse(user, 0L, 0L)).thenReturn(mappedResponse);
        when(shopRepository.findByOwnerId(1L)).thenReturn(Optional.of(shop));

        UserResponse response = userService.getUserByUsername("seller_user");

        assertThat(response.getShopId()).isNull();
        assertThat(response.getShopName()).isNull();
        assertThat(response.getShopAvatarUrl()).isNull();
    }
}
