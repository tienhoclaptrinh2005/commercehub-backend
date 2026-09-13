package com.commercehub.backend.admin.service;

import com.commercehub.backend.admin.dto.AdminRequests;
import com.commercehub.backend.auth.repository.RefreshTokenRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.shop.service.ShopService;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.repository.WithdrawalRepository;
import com.commercehub.backend.wallet.service.WithdrawalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCommandServiceTest {
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock ShopRepository shopRepository;
    @Mock ShopService shopService;
    @Mock ProductRepository productRepository;
    @Mock WithdrawalRepository withdrawalRepository;
    @Mock WithdrawalService withdrawalService;
    @Mock AdminAuditService auditService;
    @InjectMocks AdminCommandService service;

    @Test
    void normalAdminCannotLockSuperAdmin() {
        User actor = user(1L, "ADMIN");
        User target = user(2L, "SUPER_ADMIN");
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(actor));
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.changeUserStatus(1L, 2L,
                new AdminRequests.StatusChange("BANNED", "test"), "127.0.0.1", "JUnit"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminBanUsesDedicatedProductStatusAndWritesAudit() {
        Shop shop = Shop.builder().id(8L).build();
        Product product = Product.builder().id(9L).shop(shop).status("ACTIVE").build();
        when(productRepository.findById(9L)).thenReturn(Optional.of(product));

        service.changeProductStatus(1L, 9L,
                new AdminRequests.StatusChange("BANNED", "Nội dung vi phạm"), "127.0.0.1", "JUnit");

        verify(productRepository).save(argThat(saved -> "BANNED".equals(saved.getStatus())));
        verify(auditService).record(eq(1L), eq("PRODUCT_STATUS_CHANGED"), eq("PRODUCT"), eq(9L),
                anyMap(), anyMap(), eq("Nội dung vi phạm"), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void staleShopVersionCannotOverwriteAnotherAdminDecision() {
        Shop shop = Shop.builder().id(5L).version(4L).status("PENDING").build();
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> service.changeShopStatus(1L, 5L,
                new AdminRequests.ShopStatusChange("ACTIVE", null, 3L), "127.0.0.1", "JUnit"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHOP_REVIEW_CONFLICT);
        verifyNoInteractions(shopService, auditService);
    }

    private static User user(Long id, String roleName) {
        Role role = new Role(); role.setName(roleName);
        return User.builder().id(id).status("ACTIVE").roles(new HashSet<>(Set.of(role))).build();
    }
}
