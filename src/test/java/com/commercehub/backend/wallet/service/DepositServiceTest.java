package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.entity.Deposit;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.DepositRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositServiceTest {

    private final DepositRepository depositRepository = mock(DepositRepository.class);
    private final WalletService walletService = mock(WalletService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final DepositService service = new DepositService(
            depositRepository,
            walletService,
            userRepository,
            walletRepository
    );

    @Test
    void createsPendingSePayDeposit() {
        User user = User.builder().id(5L).build();
        Wallet wallet = Wallet.builder().id(8L).user(user).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(5L)).thenReturn(Optional.of(wallet));

        service.createPendingDeposit(5L, new BigDecimal("100000"), "SEPAY_TEST_1");

        ArgumentCaptor<Deposit> captor = ArgumentCaptor.forClass(Deposit.class);
        verify(depositRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("SEPAY");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getTransactionCode()).isEqualTo("SEPAY_TEST_1");
    }

    @Test
    void successfulIpnPersistsProviderTransactionBeforeCreditingWallet() {
        User user = User.builder().id(5L).build();
        Deposit deposit = Deposit.builder()
                .id(10L)
                .user(user)
                .amount(new BigDecimal("100000"))
                .provider("SEPAY")
                .transactionCode("SEPAY_TEST_1")
                .status("PENDING")
                .build();
        when(depositRepository.findByTransactionCodeWithLock("SEPAY_TEST_1"))
                .thenReturn(Optional.of(deposit));
        when(depositRepository.existsByProviderTransactionId("SEPAY_TRANSACTION_1"))
                .thenReturn(false);

        service.processSuccess("SEPAY_TEST_1", new BigDecimal("100000"), "SEPAY_TRANSACTION_1");

        assertThat(deposit.getStatus()).isEqualTo("SUCCESS");
        assertThat(deposit.getProviderTransactionId()).isEqualTo("SEPAY_TRANSACTION_1");
        assertThat(deposit.getProcessedAt()).isNotNull();
        verify(depositRepository).saveAndFlush(deposit);
        verify(walletService).systemCreditBalance(
                5L,
                new BigDecimal("100000"),
                "DEPOSIT",
                10L,
                "DEPOSIT"
        );
    }

    @Test
    void duplicateIpnDoesNotCreditWalletTwice() {
        Deposit deposit = Deposit.builder()
                .status("SUCCESS")
                .amount(new BigDecimal("100000"))
                .build();
        when(depositRepository.findByTransactionCodeWithLock("SEPAY_TEST_1"))
                .thenReturn(Optional.of(deposit));

        service.processSuccess("SEPAY_TEST_1", new BigDecimal("100000"), "SEPAY_TRANSACTION_1");

        verify(walletService, never()).systemCreditBalance(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void rejectsReusedProviderTransactionId() {
        Deposit deposit = Deposit.builder()
                .status("PENDING")
                .amount(new BigDecimal("100000"))
                .build();
        when(depositRepository.findByTransactionCodeWithLock("SEPAY_TEST_1"))
                .thenReturn(Optional.of(deposit));
        when(depositRepository.existsByProviderTransactionId("SEPAY_TRANSACTION_1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.processSuccess(
                "SEPAY_TEST_1",
                new BigDecimal("100000"),
                "SEPAY_TRANSACTION_1"
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_TRANSACTION_ALREADY_PROCESSED));

        verify(walletService, never()).systemCreditBalance(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
