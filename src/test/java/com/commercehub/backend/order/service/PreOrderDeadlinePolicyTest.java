package com.commercehub.backend.order.service;

import com.commercehub.backend.common.policy.PreOrderPolicy;
import com.commercehub.backend.fee.dto.FeeResult;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
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
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreOrderDeadlinePolicyTest {

    @Test
    void checkoutGivesSellerExactlyTwentyFourHoursToAccept() {
        ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PreOrderItemRepository preOrderItemRepository = mock(PreOrderItemRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrderStatusService orderStatusService = mock(OrderStatusService.class);
        WalletService walletService = mock(WalletService.class);
        FeeCalculationService feeCalculationService = mock(FeeCalculationService.class);
        PreOrderService service = new PreOrderService(
                variantRepository,
                orderRepository,
                orderItemRepository,
                preOrderItemRepository,
                userRepository,
                orderStatusService,
                walletService,
                feeCalculationService
        );

        User buyer = User.builder().id(1L).build();
        Role sellerRole = new Role();
        sellerRole.setName("SELLER");
        User seller = User.builder()
                .id(2L)
                .status("ACTIVE")
                .roles(new HashSet<>(Set.of(sellerRole)))
                .build();
        Shop shop = Shop.builder().id(3L).owner(seller).status("ACTIVE").build();
        Product product = Product.builder()
                .id(4L)
                .shop(shop)
                .name("Dịch vụ test")
                .deliveryType("PRE_ORDER")
                .status("ACTIVE")
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(5L)
                .product(product)
                .name("Mặc định")
                .price(new BigDecimal("100000.00"))
                .status("ACTIVE")
                .build();
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductVariantId(variant.getId());
        item.setQuantity(1);
        CheckoutRequest request = new CheckoutRequest();
        request.setItems(List.of(item));
        request.setIdempotencyKey("pre-order-policy-test-key");

        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(variantRepository.findCheckoutVariants(Set.of(5L))).thenReturn(List.of(variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(40L);
            return saved;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(feeCalculationService.calculateFee(new BigDecimal("100000.00"))).thenReturn(
                FeeResult.builder()
                        .feeConfigId(1L)
                        .feeRateSnapshot(new BigDecimal("0.0400"))
                        .feeAmount(new BigDecimal("4000.00"))
                        .sellerNetAmount(new BigDecimal("96000.00"))
                        .build()
        );

        OffsetDateTime before = OffsetDateTime.now();
        service.checkoutPreOrder(1L, request);
        OffsetDateTime after = OffsetDateTime.now();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getApprovalDeadlineAt())
                .isBetween(
                        before.plusHours(PreOrderPolicy.ACCEPTANCE_HOURS),
                        after.plusHours(PreOrderPolicy.ACCEPTANCE_HOURS)
                );
    }

    @Test
    void acceptingOrderGivesSellerExactlyTwentyFourHoursToComplete() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PreOrderItemRepository preOrderItemRepository = mock(PreOrderItemRepository.class);
        OrderStatusService orderStatusService = mock(OrderStatusService.class);
        PreOrderApprovalService service = new PreOrderApprovalService(
                orderRepository,
                orderItemRepository,
                preOrderItemRepository,
                orderStatusService,
                mock(WalletService.class),
                mock(FeeCalculationService.class),
                mock(PlatformFeeLedgerRepository.class),
                mock(HoldReleaseRepository.class),
                mock(WalletRepository.class)
        );

        User seller = User.builder().id(2L).build();
        Shop shop = Shop.builder().id(3L).owner(seller).build();
        Order order = Order.builder()
                .id(40L)
                .shop(shop)
                .deliveryType("PRE_ORDER")
                .status("WAITING_APPROVAL")
                .approvalDeadlineAt(OffsetDateTime.now().plusHours(1))
                .build();
        when(orderRepository.findByIdWithLock(40L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of());
        when(preOrderItemRepository.findByOrderItemIdIn(List.of())).thenReturn(List.of());

        OffsetDateTime before = OffsetDateTime.now();
        service.acceptOrder(2L, 40L);
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(order.getStatus()).isEqualTo("PROCESSING");
        assertThat(order.getProcessingDeadlineAt())
                .isBetween(
                        before.plusHours(PreOrderPolicy.PROCESSING_HOURS),
                        after.plusHours(PreOrderPolicy.PROCESSING_HOURS)
                );
    }
}
