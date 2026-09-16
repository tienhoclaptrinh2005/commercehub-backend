package com.commercehub.backend.voucher.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.voucher.dto.request.VoucherRequest;
import com.commercehub.backend.voucher.entity.*;
import com.commercehub.backend.voucher.repository.VoucherRepository;
import com.commercehub.backend.voucher.repository.VoucherUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class VoucherServiceTest {
    private VoucherRepository voucherRepository;
    private VoucherUsageRepository usageRepository;
    private ShopRepository shopRepository;
    private VoucherService service;
    private Shop shop;

    @BeforeEach
    void setUp() {
        voucherRepository = mock(VoucherRepository.class);
        usageRepository = mock(VoucherUsageRepository.class);
        shopRepository = mock(ShopRepository.class);
        service = new VoucherService(
                voucherRepository,
                usageRepository,
                shopRepository,
                mock(com.commercehub.backend.product.repository.ProductRepository.class),
                mock(com.commercehub.backend.product.repository.ProductVariantRepository.class),
                mock(com.commercehub.backend.user.repository.UserRepository.class)
        );
        Role sellerRole = new Role();
        sellerRole.setName("SELLER");
        User seller = User.builder().id(3L).status("ACTIVE")
                .roles(new java.util.HashSet<>(java.util.Set.of(sellerRole))).build();
        shop = Shop.builder().id(10L).owner(seller).status("ACTIVE").build();
    }

    @Test
    void createDefaultsMissingMinimumOrderAmountToZero() {
        VoucherRequest request = new VoucherRequest();
        request.setCode("NO_MINIMUM");
        request.setDiscountType(VoucherDiscountType.PERCENT);
        request.setDiscountValue(new BigDecimal("10.00"));
        request.setApplyAllProducts(true);
        request.setStartsAt(OffsetDateTime.now().minusMinutes(1));
        request.setExpiresAt(OffsetDateTime.now().plusDays(1));
        request.setUsageLimit(100);

        when(shopRepository.findByOwnerId(3L)).thenReturn(Optional.of(shop));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(3L, request);

        assertThat(response.minOrderAmount()).isEqualByComparingTo("0.00");
        verify(voucherRepository).save(argThat(voucher ->
                voucher.getMinOrderAmount().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    void reserveCalculatesPercentDiscountAndAllocatesEveryCentExactly() {
        Voucher voucher = activeVoucher();
        when(voucherRepository.findForUpdate(10L, "SALE10")).thenReturn(Optional.of(voucher));
        when(usageRepository.existsByVoucherIdAndUserIdAndStatus(20L, 3L, VoucherUsageStatus.APPLIED))
                .thenReturn(false);

        VoucherService.VoucherApplication result = service.reserve(3L, 10L, "sale10", List.of(
                new VoucherService.VoucherLine(101L, new BigDecimal("15000.00")),
                new VoucherService.VoucherLine(102L, new BigDecimal("5000.00"))
        ));

        assertThat(result.subtotalAmount()).isEqualByComparingTo("20000.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("2000.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("18000.00");
        assertThat(result.lineDiscounts()).containsExactly(
                new BigDecimal("1500.00"), new BigDecimal("500.00"));
        assertThat(result.lineDiscounts().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.discountAmount());
        assertThat(voucher.getUsedCount()).isEqualTo(1);
        verify(voucherRepository).save(voucher);
    }

    @Test
    void reserveRejectsBuyerWhoAlreadyHasAnAppliedUsage() {
        Voucher voucher = activeVoucher();
        when(voucherRepository.findForUpdate(10L, "SALE10")).thenReturn(Optional.of(voucher));
        when(usageRepository.existsByVoucherIdAndUserIdAndStatus(20L, 3L, VoucherUsageStatus.APPLIED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reserve(3L, 10L, "SALE10", List.of(
                new VoucherService.VoucherLine(101L, new BigDecimal("20000.00")))))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VOUCHER_ALREADY_USED));
        assertThat(voucher.getUsedCount()).isZero();
    }

    @Test
    void releasedUsageReturnsCapacityWithoutDeletingAuditHistory() {
        Voucher voucher = activeVoucher();
        voucher.setUsedCount(7);
        VoucherUsage usage = VoucherUsage.builder()
                .id(30L)
                .voucher(voucher)
                .status(VoucherUsageStatus.APPLIED)
                .build();
        when(usageRepository.findByOrderIdAndStatus(40L, VoucherUsageStatus.APPLIED))
                .thenReturn(Optional.of(usage));
        when(voucherRepository.findForUpdate(10L, "SALE10")).thenReturn(Optional.of(voucher));

        service.releaseUsageForCancelledOrder(40L);

        assertThat(usage.getStatus()).isEqualTo(VoucherUsageStatus.RELEASED);
        assertThat(usage.getReleasedAt()).isNotNull();
        assertThat(voucher.getUsedCount()).isEqualTo(6);
        verify(usageRepository).save(usage);
        verify(voucherRepository).save(voucher);
        verify(usageRepository, never()).delete(any());
    }

    private Voucher activeVoucher() {
        return Voucher.builder()
                .id(20L)
                .shop(shop)
                .code("SALE10")
                .discountType(VoucherDiscountType.PERCENT)
                .discountValue(new BigDecimal("10.00"))
                .minOrderAmount(new BigDecimal("10000.00"))
                .applyAllProducts(true)
                .products(new LinkedHashSet<>())
                .startsAt(OffsetDateTime.now().minusHours(1))
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .usageLimit(100)
                .usedCount(0)
                .active(true)
                .build();
    }
}
