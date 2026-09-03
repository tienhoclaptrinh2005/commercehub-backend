package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.response.SliceResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.wallet.dto.response.WalletTransactionResponse;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.WalletTransaction;
import com.commercehub.backend.wallet.mapper.WalletMapper;
import com.commercehub.backend.wallet.repository.DepositRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WalletTransactionServiceTest {

    @Test
    void sellerHistoryExcludesBuyerAndDepositTransactions() {
        WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        WalletMapper walletMapper = mock(WalletMapper.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        DepositRepository depositRepository = mock(DepositRepository.class);
        WalletTransactionService service = new WalletTransactionService(
                transactionRepository,
                walletRepository,
                walletMapper,
                orderRepository,
                orderItemRepository,
                depositRepository
        );

        Wallet wallet = Wallet.builder().id(4L).build();
        PageRequest pageable = PageRequest.of(0, 10);
        List<String> sellerTypes = List.of(
                "SALE_HOLD",
                "HOLD_RELEASE",
                "HOLD_RELEASE_NET",
                "CANCEL_HOLD",
                "PLATFORM_FEE",
                "WITHDRAW_PENDING",
                "WITHDRAW_DONE",
                "WITHDRAW_CANCEL",
                "ADMIN_ADJUST"
        );

        when(walletRepository.findByUserId(3L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndTransactionTypeInOrderByCreatedAtDesc(
                4L, sellerTypes, pageable
        )).thenReturn(new SliceImpl<>(List.of(), pageable, false));

        SliceResponse<WalletTransactionResponse> result =
                service.getSellerTransactions(3L, 1, 10, "ALL");

        assertThat(result.getData()).isEmpty();
        verify(transactionRepository).findByWalletIdAndTransactionTypeInOrderByCreatedAtDesc(
                4L, sellerTypes, pageable);
        verify(transactionRepository, never()).findByWalletIdOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void returnsThreeNavigationPagesWithoutTotalCount() {
        WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        WalletMapper walletMapper = mock(WalletMapper.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        DepositRepository depositRepository = mock(DepositRepository.class);
        WalletTransactionService service = new WalletTransactionService(
                transactionRepository,
                walletRepository,
                walletMapper,
                orderRepository,
                orderItemRepository,
                depositRepository
        );

        Wallet wallet = Wallet.builder().id(4L).build();
        WalletTransaction transaction = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .walletId(4L)
                .transactionType("ADMIN_ADJUST")
                .balanceType("AVAILABLE")
                .amount(BigDecimal.ONE)
                .balanceBefore(BigDecimal.ZERO)
                .balanceAfter(BigDecimal.ONE)
                .createdAt(OffsetDateTime.now())
                .build();
        PageRequest firstPage = PageRequest.of(0, 1);
        PageRequest secondPage = PageRequest.of(1, 1);

        when(walletRepository.findByUserId(3L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdOrderByCreatedAtDesc(4L, firstPage))
                .thenReturn(new SliceImpl<>(List.of(transaction), firstPage, true));
        when(transactionRepository.findByWalletIdOrderByCreatedAtDesc(4L, secondPage))
                .thenReturn(new SliceImpl<>(List.of(transaction), secondPage, true));
        when(walletMapper.toTransactionResponse(transaction)).thenReturn(
                WalletTransactionResponse.builder()
                        .id(transaction.getId())
                        .transactionType(transaction.getTransactionType())
                        .balanceType(transaction.getBalanceType())
                        .amount(transaction.getAmount())
                        .balanceBefore(transaction.getBalanceBefore())
                        .balanceAfter(transaction.getBalanceAfter())
                        .createdAt(transaction.getCreatedAt())
                        .build()
        );

        SliceResponse<WalletTransactionResponse> result =
                service.getMyTransactions(3L, 1, 1, "ALL");

        assertThat(result.getPageNumbers()).containsExactly(1, 2, 3);
        assertThat(result.isHasNext()).isTrue();
        verify(transactionRepository).findByWalletIdOrderByCreatedAtDesc(4L, firstPage);
        verify(transactionRepository).findByWalletIdOrderByCreatedAtDesc(4L, secondPage);
    }

    @Test
    void paymentHistoryContainsRealOrderAndProductSnapshot() {
        WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        WalletMapper walletMapper = mock(WalletMapper.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        DepositRepository depositRepository = mock(DepositRepository.class);
        WalletTransactionService service = new WalletTransactionService(
                transactionRepository,
                walletRepository,
                walletMapper,
                orderRepository,
                orderItemRepository,
                depositRepository
        );

        Wallet wallet = Wallet.builder().id(4L).build();
        Order order = Order.builder().id(40L).orderCode("ORD-S1-TEST-40").build();
        WalletTransaction transaction = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .walletId(4L)
                .transactionType("ORDER_PAYMENT")
                .balanceType("AVAILABLE")
                .amount(new BigDecimal("-59000.00"))
                .balanceBefore(new BigDecimal("2797000.00"))
                .balanceAfter(new BigDecimal("2738000.00"))
                .referenceId(40L)
                .referenceType("ORDER_PRE")
                .createdAt(OffsetDateTime.now())
                .build();
        OrderItem item = OrderItem.builder()
                .id(100L)
                .order(order)
                .productName("Netflix Family")
                .variantName("Gói 1 tháng")
                .quantity(1)
                .build();

        when(walletRepository.findByUserId(3L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndTransactionTypeInOrderByCreatedAtDesc(
                4L,
                List.of("ORDER_PAYMENT"),
                PageRequest.of(0, 15)
        )).thenReturn(new PageImpl<>(List.of(transaction), PageRequest.of(0, 15), 1));
        when(orderRepository.findAllById(List.of(40L))).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderIdIn(List.of(40L))).thenReturn(List.of(item));
        when(walletMapper.toTransactionResponse(transaction)).thenReturn(
                WalletTransactionResponse.builder()
                        .id(transaction.getId())
                        .transactionType(transaction.getTransactionType())
                        .balanceType(transaction.getBalanceType())
                        .amount(transaction.getAmount())
                        .balanceBefore(transaction.getBalanceBefore())
                        .balanceAfter(transaction.getBalanceAfter())
                        .createdAt(transaction.getCreatedAt())
                        .build()
        );

        SliceResponse<WalletTransactionResponse> result =
                service.getMyTransactions(3L, 1, 15, "payment");

        assertThat(result.getData()).hasSize(1);
        WalletTransactionResponse response = result.getData().getFirst();
        assertThat(response.getReferenceId()).isEqualTo(40L);
        assertThat(response.getReferenceType()).isEqualTo("ORDER_PRE");
        assertThat(response.getReferenceCode()).isEqualTo("ORD-S1-TEST-40");
        assertThat(response.getDescription())
                .isEqualTo("Thanh toán ORD-S1-TEST-40: Netflix Family - Gói 1 tháng ×1");
        verify(transactionRepository, never()).findByWalletIdOrderByCreatedAtDesc(anyLong(), any());
    }
}
