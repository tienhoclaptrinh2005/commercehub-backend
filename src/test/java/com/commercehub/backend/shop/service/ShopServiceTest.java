package com.commercehub.backend.shop.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.dto.response.ShopApplicationResponse;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.mapper.ShopMapper;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.RoleRepository;
import com.commercehub.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShopServiceTest {

    private ShopRepository shopRepository;
    private ShopMapper shopMapper;
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private ShopService service;

    @BeforeEach
    void setUp() {
        shopRepository = mock(ShopRepository.class);
        shopMapper = mock(ShopMapper.class);
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        service = new ShopService(
                shopRepository,
                shopMapper,
                userRepository,
                roleRepository,
                mock(OrderStatisticsService.class)
        );
    }

    @Test
    void submittingApplicationKeepsBuyerRoleAndLocksChosenUsername() {
        User buyer = user(1L, "buyer_generated", "Buyer Name", "BUYER");
        CreateShopRequest request = request("Digital Store", "digital.store");
        Shop pendingShop = Shop.builder().status("PENDING").build();
        ShopApplicationResponse response = ShopApplicationResponse.builder()
                .id(10L)
                .status("PENDING")
                .build();

        when(userRepository.findByIdForUsernameUpdate(1L)).thenReturn(Optional.of(buyer));
        when(shopRepository.findByOwnerId(1L)).thenReturn(Optional.empty());
        when(shopRepository.existsByNameIgnoreCase("Digital Store")).thenReturn(false);
        when(userRepository.existsByUsername("digital.store")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopMapper.toEntity(request)).thenReturn(pendingShop);
        when(shopRepository.existsBySlug("digital-store")).thenReturn(false);
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopMapper.toApplicationResponse(pendingShop)).thenReturn(response);

        ShopApplicationResponse created = service.createShop(request, 1L);

        assertThat(created.getStatus()).isEqualTo("PENDING");
        assertThat(buyer.getUsername()).isEqualTo("digital.store");
        assertThat(buyer.getUsernameChangedAt()).isNotNull();
        assertThat(buyer.getRoles()).extracting(Role::getName).containsExactly("BUYER");
        assertThat(pendingShop.getContactInfo()).isEqualTo("@digital_support");
    }

    @Test
    void approvingPendingShopPromotesOwnerAndSynchronizesDisplayName() {
        User buyer = user(1L, "digital.store", "Buyer Name", "BUYER");
        Shop shop = Shop.builder()
                .id(10L)
                .owner(buyer)
                .name("Digital Store")
                .status("PENDING")
                .version(0L)
                .build();
        Role sellerRole = role("SELLER");

        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(roleRepository.findByName("SELLER")).thenReturn(Optional.of(sellerRole));

        service.changeShopStatus(10L, "ACTIVE");

        assertThat(shop.getStatus()).isEqualTo("ACTIVE");
        assertThat(buyer.getFullName()).isEqualTo("Digital Store");
        assertThat(buyer.getRoles()).extracting(Role::getName).containsExactly("SELLER");
        verify(shopRepository).saveAndFlush(shop);
    }

    @Test
    void rejectedApplicationIsTerminal() {
        Shop rejected = Shop.builder().id(10L).status("REJECTED").build();
        when(shopRepository.findById(10L)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.changeShopStatus(10L, "ACTIVE"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SHOP_STATUS_TRANSITION_INVALID));
    }

    @Test
    void concurrentReviewConflictStopsBeforeChangingBuyerRole() {
        User buyer = user(1L, "digital.store", "Buyer Name", "BUYER");
        Shop shop = Shop.builder()
                .id(10L)
                .owner(buyer)
                .name("Digital Store")
                .status("PENDING")
                .version(0L)
                .build();

        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(shopRepository.saveAndFlush(shop))
                .thenThrow(new ObjectOptimisticLockingFailureException(Shop.class, 10L));

        assertThatThrownBy(() -> service.changeShopStatus(10L, "ACTIVE"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SHOP_REVIEW_CONFLICT));

        assertThat(buyer.getRoles()).extracting(Role::getName).containsExactly("BUYER");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void removedShopStatusesAreRejected() {
        Shop activeShop = Shop.builder().id(10L).status("ACTIVE").build();
        when(shopRepository.findById(10L)).thenReturn(Optional.of(activeShop));

        for (String removedStatus : Set.of("INACTIVE", "SUSPENDED", "CLOSED")) {
            assertThatThrownBy(() -> service.changeShopStatus(10L, removedStatus))
                    .isInstanceOfSatisfying(AppException.class, exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_STATUS));
        }
    }

    private CreateShopRequest request(String name, String username) {
        CreateShopRequest request = new CreateShopRequest();
        request.setName(name);
        request.setUsername(username);
        request.setContactInfo("@digital_support");
        request.setApplicationReason("Nguồn hàng số chính chủ");
        request.setAcceptedTerms(true);
        return request;
    }

    private User user(Long id, String username, String fullName, String roleName) {
        return User.builder()
                .id(id)
                .username(username)
                .fullName(fullName)
                .roles(new HashSet<>(Set.of(role(roleName))))
                .build();
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
