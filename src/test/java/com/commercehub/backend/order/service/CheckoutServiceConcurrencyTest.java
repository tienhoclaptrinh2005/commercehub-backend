package com.commercehub.backend.order.service;

import com.commercehub.backend.order.dto.request.CheckoutItemRequest;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.entity.CheckoutAttempt;
import com.commercehub.backend.order.repository.CheckoutAttemptRepository;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckoutServiceConcurrencyTest {

    @Test
    void sameBuyerAndKeyCreatesOrdersOnlyOnce() throws Exception {
        ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
        InstantOrderService instantOrderService = mock(InstantOrderService.class);
        PreOrderService preOrderService = mock(PreOrderService.class);
        CheckoutAttemptRepository attemptRepository = mock(CheckoutAttemptRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        CheckoutService service = new CheckoutService(
                variantRepository,
                instantOrderService,
                preOrderService,
                attemptRepository,
                walletRepository,
                new ObjectMapper()
        );

        User buyer = User.builder().id(1L).build();
        User seller = User.builder().id(2L).build();
        Shop shop = Shop.builder().id(10L).owner(seller).status("ACTIVE").build();
        Product product = Product.builder().id(20L).shop(shop).deliveryType("INSTANT").build();
        ProductVariant variant = ProductVariant.builder().id(30L).product(product)
                .price(BigDecimal.TEN).stockCount(10).build();
        Wallet buyerWallet = Wallet.builder().id(100L).user(buyer).build();
        Wallet sellerWallet = Wallet.builder().id(101L).user(seller).build();

        when(variantRepository.findCheckoutVariants(any())).thenReturn(List.of(variant));
        when(instantOrderService.checkoutInstant(eq(1L), any())).thenReturn(501L);

        ReentrantLock simulatedDatabaseLock = new ReentrantLock();
        AtomicReference<CheckoutAttempt> stored = new AtomicReference<>();
        when(walletRepository.findAllByUserIdsWithLock(any())).thenAnswer(invocation -> {
            simulatedDatabaseLock.lock();
            return List.of(buyerWallet, sellerWallet);
        });
        when(attemptRepository.findForUpdate(1L, "CHECKOUT", "1234567890abcdef"))
                .thenAnswer(invocation -> {
                    CheckoutAttempt attempt = stored.get();
                    if (attempt != null && "DONE".equals(attempt.getStatus())) {
                        simulatedDatabaseLock.unlock();
                    }
                    return Optional.ofNullable(attempt);
                });
        when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            attempt.setId(900L);
            stored.set(attempt);
            return attempt;
        });
        when(attemptRepository.save(any())).thenAnswer(invocation -> {
            CheckoutAttempt attempt = invocation.getArgument(0);
            stored.set(attempt);
            if ("DONE".equals(attempt.getStatus()) && simulatedDatabaseLock.isHeldByCurrentThread()) {
                simulatedDatabaseLock.unlock();
            }
            return attempt;
        });

        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductVariantId(30L);
        item.setQuantity(1);
        CheckoutRequest request = new CheckoutRequest();
        request.setItems(List.of(item));
        request.setPaymentMethod("WALLET");
        request.setIdempotencyKey("1234567890abcdef");

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.processCheckout(1L, request); });
            var second = executor.submit(() -> { start.await(); return service.processCheckout(1L, request); });
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(501L);
            assertThat(second.get(5, TimeUnit.SECONDS)).containsExactly(501L);
        }

        verify(instantOrderService, times(1)).checkoutInstant(eq(1L), any());
        verify(preOrderService, never()).checkoutPreOrder(anyLong(), any());
    }
}
