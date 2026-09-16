package com.commercehub.backend.order.service;

import com.commercehub.backend.fee.dto.FeeResult;
import com.commercehub.backend.fee.service.FeeCalculationService;
import com.commercehub.backend.order.dto.request.CheckoutItemRequest;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.voucher.entity.Voucher;
import com.commercehub.backend.voucher.service.VoucherService;
import com.commercehub.backend.voucher.service.VoucherService.VoucherApplication;
import com.commercehub.backend.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreOrderVoucherCheckoutTest {

    @Test
    void voucherDiscountIsPersistedAndOnlyDiscountedTotalMovesThroughWalletsAndFees() {
        ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PreOrderItemRepository preOrderItemRepository = mock(PreOrderItemRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrderStatusService orderStatusService = mock(OrderStatusService.class);
        WalletService walletService = mock(WalletService.class);
        FeeCalculationService feeCalculationService = mock(FeeCalculationService.class);
        VoucherService voucherService = mock(VoucherService.class);
        PreOrderService service = new PreOrderService(
                variantRepository, orderRepository, orderItemRepository, preOrderItemRepository,
                userRepository, orderStatusService, walletService, feeCalculationService, voucherService
        );

        User buyer = User.builder().id(1L).build();
        Role sellerRole = new Role();
        sellerRole.setName("SELLER");
        User seller = User.builder().id(2L).status("ACTIVE")
                .roles(new HashSet<>(Set.of(sellerRole))).build();
        Shop shop = Shop.builder().id(3L).owner(seller).status("ACTIVE").build();
        Product product = Product.builder().id(4L).shop(shop).name("Dịch vụ test")
                .deliveryType("PRE_ORDER").status("ACTIVE").build();
        ProductVariant variant = ProductVariant.builder().id(5L).product(product).name("Mặc định")
                .price(new BigDecimal("100000.00")).status("ACTIVE").build();

        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductVariantId(5L);
        item.setQuantity(1);
        CheckoutRequest request = new CheckoutRequest();
        request.setItems(List.of(item));
        request.setVoucherCode("SALE10");

        Voucher voucher = Voucher.builder().id(9L).shop(shop).code("SALE10").build();
        VoucherApplication application = new VoucherApplication(
                voucher,
                new BigDecimal("100000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("90000.00"),
                List.of(new BigDecimal("10000.00"))
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(variantRepository.findCheckoutVariants(Set.of(5L))).thenReturn(List.of(variant));
        when(voucherService.reserve(eq(1L), eq(3L), eq("SALE10"), any())).thenReturn(application);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(40L);
            return saved;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(feeCalculationService.calculateFee(new BigDecimal("90000.00"))).thenReturn(
                FeeResult.builder()
                        .feeConfigId(11L)
                        .feeRateSnapshot(new BigDecimal("0.0400"))
                        .feeAmount(new BigDecimal("3600.00"))
                        .sellerNetAmount(new BigDecimal("86400.00"))
                        .build()
        );

        service.checkoutPreOrder(1L, request);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getSubtotalAmount()).isEqualByComparingTo("100000.00");
        assertThat(savedOrder.getVoucherId()).isEqualTo(9L);
        assertThat(savedOrder.getVoucherDiscount()).isEqualByComparingTo("10000.00");
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("90000.00");

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getLineSubtotal()).isEqualByComparingTo("100000.00");
        assertThat(itemCaptor.getValue().getVoucherDiscount()).isEqualByComparingTo("10000.00");
        assertThat(itemCaptor.getValue().getLineTotal()).isEqualByComparingTo("90000.00");

        verify(walletService).deductBalance(1L, new BigDecimal("90000.00"),
                "ORDER_PAYMENT", 40L, "ORDER_PRE");
        verify(walletService).systemHoldForSeller(2L, new BigDecimal("90000.00"), 40L);
        verify(feeCalculationService).calculateFee(new BigDecimal("90000.00"));
        verify(voucherService).confirmUsage(same(application), eq(1L), same(savedOrder));
    }
}
