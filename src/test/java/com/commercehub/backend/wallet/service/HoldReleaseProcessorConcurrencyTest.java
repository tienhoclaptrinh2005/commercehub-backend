package com.commercehub.backend.wallet.service;

import com.commercehub.backend.fee.service.PlatformFeeLedgerService;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.entity.HoldReleaseStatus;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.mockito.Mockito.*;

class HoldReleaseProcessorConcurrencyTest {

    @Test
    void concurrentReleaseMovesMoneyAndCountsSaleOnlyOnce() throws Exception {
        WalletService walletService = mock(WalletService.class);
        HoldReleaseRepository holdRepository = mock(HoldReleaseRepository.class);
        PlatformFeeLedgerService feeService = mock(PlatformFeeLedgerService.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        HoldReleaseProcessor processor = new HoldReleaseProcessor(
                walletService, holdRepository, feeService, productRepository);

        User seller = User.builder().id(2L).build();
        Wallet wallet = Wallet.builder().id(20L).user(seller).build();
        HoldRelease hold = HoldRelease.builder()
                .id(30L).wallet(wallet).orderId(40L).orderItemId(50L)
                .holdAmount(new BigDecimal("100.00"))
                .feeAmount(new BigDecimal("4.00"))
                .sellerNetAmount(new BigDecimal("96.00"))
                .feeLedgerId(60L).status(HoldReleaseStatus.HOLDING)
                .scheduledReleaseAt(OffsetDateTime.now().minusMinutes(1)).build();

        ReentrantLock simulatedDatabaseLock = new ReentrantLock();
        when(holdRepository.findByIdWithLock(30L)).thenAnswer(invocation -> {
            simulatedDatabaseLock.lock();
            if (hold.getStatus() != HoldReleaseStatus.HOLDING) {
                simulatedDatabaseLock.unlock();
            }
            return Optional.of(hold);
        });
        when(holdRepository.save(any())).thenAnswer(invocation -> {
            if (simulatedDatabaseLock.isHeldByCurrentThread()) simulatedDatabaseLock.unlock();
            return invocation.getArgument(0);
        });
        when(productRepository.incrementSoldCountByOrderItemId(50L)).thenReturn(1);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); processor.processSingle(30L); return null; });
            var second = executor.submit(() -> { start.await(); processor.processSingle(30L); return null; });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        verify(walletService, times(1)).systemReleaseHold(
                eq(2L), eq(new BigDecimal("100.00")), eq(new BigDecimal("96.00")),
                eq(new BigDecimal("4.00")), eq(30L));
        verify(feeService, times(1)).markAsCollected(60L);
        verify(productRepository, times(1)).incrementSoldCountByOrderItemId(50L);
    }
}
