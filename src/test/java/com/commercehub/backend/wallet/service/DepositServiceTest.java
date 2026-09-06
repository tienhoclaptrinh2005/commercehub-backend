package com.commercehub.backend.wallet.service;

import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.wallet.config.SePayProperties;
import com.commercehub.backend.wallet.dto.request.DepositRequest;
import com.commercehub.backend.wallet.dto.response.DepositQrResponse;
import com.commercehub.backend.wallet.entity.Deposit;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.DepositRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositServiceTest {

    private final DepositRepository depositRepository = mock(DepositRepository.class);
    private final WalletService walletService = mock(WalletService.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final VietQrService vietQrService = mock(VietQrService.class);
    private final SePayProperties properties = validProperties();
    private final DepositService service = new DepositService(
            depositRepository,
            walletService,
            walletRepository,
            vietQrService,
            properties
    );

    @Test
    void createsPendingDepositAndReturnsFifteenMinuteQrSession() {
        User user = User.builder().id(5L).build();
        Wallet wallet = Wallet.builder().id(8L).user(user).build();
        DepositRequest request = new DepositRequest();
        request.setAmount(new BigDecimal("100000"));
        request.setIdempotencyKey("idem-1");

        when(walletRepository.findByUserIdWithLock(5L)).thenReturn(Optional.of(wallet));
        when(depositRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(depositRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(5L, "PENDING"))
                .thenReturn(Optional.empty());
        when(depositRepository.findFirstByUserIdOrderByCreatedAtDesc(5L)).thenReturn(Optional.empty());
        when(depositRepository.existsByTransactionCode(anyString())).thenReturn(false);
        when(vietQrService.buildQrUrl(any(), anyString())).thenReturn("https://vietqr.app/img?test=1");
        when(vietQrService.getBankCode()).thenReturn("MB");
        when(vietQrService.getBankAccountNumber()).thenReturn("123456789");
        when(vietQrService.getAccountName()).thenReturn("COMMERCEHUB");

        DepositQrResponse response = service.createDeposit(5L, request);

        ArgumentCaptor<Deposit> captor = ArgumentCaptor.forClass(Deposit.class);
        verify(depositRepository).saveAndFlush(captor.capture());
        Deposit saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo("SEPAY");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getTransactionCode()).matches("CH_\\d{8}");
        assertThat(saved.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(response.getPaymentCode()).matches("CH\\d{8}");
        assertThat(response.getExpiresAt()).isAfter(OffsetDateTime.now().plusMinutes(14));
    }

    @Test
    void exactTransferBeforeExpiryCreditsWalletOnce() {
        OffsetDateTime paidAt = OffsetDateTime.now();
        User user = User.builder().id(5L).build();
        Wallet wallet = Wallet.builder().id(8L).user(user).build();
        Deposit deposit = pendingDeposit(user, paidAt.plusMinutes(5));
        when(depositRepository.findByTransactionCode("CH_12345678")).thenReturn(Optional.of(deposit));
        when(walletRepository.findByUserIdWithLock(5L)).thenReturn(Optional.of(wallet));
        when(depositRepository.findByTransactionCodeWithLock("CH_12345678")).thenReturn(Optional.of(deposit));

        service.processBankTransfer("CH_12345678", new BigDecimal("100000"), "9001", paidAt);

        assertThat(deposit.getStatus()).isEqualTo("SUCCESS");
        assertThat(deposit.getProviderTransactionId()).isEqualTo("9001");
        verify(walletService).systemCreditBalance(5L, new BigDecimal("100000"), "DEPOSIT", 10L, "DEPOSIT");
    }

    @Test
    void repeatedWebhookForSuccessfulDepositDoesNotCreditTwice() {
        User user = User.builder().id(5L).build();
        Wallet wallet = Wallet.builder().id(8L).user(user).build();
        Deposit deposit = pendingDeposit(user, OffsetDateTime.now().plusMinutes(5));
        deposit.setStatus("SUCCESS");
        deposit.setProviderTransactionId("9001");
        when(depositRepository.findByTransactionCode("CH_12345678")).thenReturn(Optional.of(deposit));
        when(walletRepository.findByUserIdWithLock(5L)).thenReturn(Optional.of(wallet));
        when(depositRepository.findByTransactionCodeWithLock("CH_12345678")).thenReturn(Optional.of(deposit));

        service.processBankTransfer("CH_12345678", new BigDecimal("100000"), "9001", OffsetDateTime.now());

        verify(walletService, never()).systemCreditBalance(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void providerTransactionAlreadyUsedByAnotherDepositIsAcknowledgedWithoutCredit() {
        User user = User.builder().id(5L).build();
        Wallet wallet = Wallet.builder().id(8L).user(user).build();
        Deposit deposit = pendingDeposit(user, OffsetDateTime.now().plusMinutes(5));
        when(depositRepository.findByTransactionCode("CH_12345678")).thenReturn(Optional.of(deposit));
        when(walletRepository.findByUserIdWithLock(5L)).thenReturn(Optional.of(wallet));
        when(depositRepository.findByTransactionCodeWithLock("CH_12345678")).thenReturn(Optional.of(deposit));
        when(depositRepository.existsByProviderTransactionId("9001")).thenReturn(true);

        service.processBankTransfer("CH_12345678", new BigDecimal("100000"), "9001", OffsetDateTime.now());

        assertThat(deposit.getStatus()).isEqualTo("PENDING");
        verify(walletService, never()).systemCreditBalance(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void sameAmountWhileQrIsActiveReturnsExistingQrInsteadOfCreatingAnotherDeposit() {
        User user = User.builder().id(5L).build();
        Wallet wallet = Wallet.builder().id(8L).user(user).build();
        Deposit active = pendingDeposit(user, OffsetDateTime.now().plusMinutes(5));
        DepositRequest request = new DepositRequest();
        request.setAmount(new BigDecimal("100000"));
        request.setIdempotencyKey("new-attempt");
        when(walletRepository.findByUserIdWithLock(5L)).thenReturn(Optional.of(wallet));
        when(depositRepository.findByIdempotencyKey("new-attempt")).thenReturn(Optional.empty());
        when(depositRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(5L, "PENDING"))
                .thenReturn(Optional.of(active));
        when(vietQrService.buildQrUrl(any(), anyString())).thenReturn("https://vietqr.app/img?test=1");

        DepositQrResponse response = service.createDeposit(5L, request);

        assertThat(response.getTransactionCode()).isEqualTo("CH_12345678");
        verify(depositRepository, never()).saveAndFlush(any(Deposit.class));
    }

    @Test
    void lateTransferRequiresReviewAndDoesNotCreditWallet() {
        OffsetDateTime expiry = OffsetDateTime.now().minusMinutes(1);
        User user = User.builder().id(5L).build();
        Wallet wallet = Wallet.builder().id(8L).user(user).build();
        Deposit deposit = pendingDeposit(user, expiry);
        deposit.setStatus("EXPIRED");
        when(depositRepository.findByTransactionCode("CH_12345678")).thenReturn(Optional.of(deposit));
        when(walletRepository.findByUserIdWithLock(5L)).thenReturn(Optional.of(wallet));
        when(depositRepository.findByTransactionCodeWithLock("CH_12345678")).thenReturn(Optional.of(deposit));

        service.processBankTransfer(
                "CH_12345678", new BigDecimal("100000"), "9002", expiry.plusSeconds(1));

        assertThat(deposit.getStatus()).isEqualTo("REVIEW_REQUIRED");
        verify(walletService, never()).systemCreditBalance(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    private static Deposit pendingDeposit(User user, OffsetDateTime expiresAt) {
        return Deposit.builder()
                .id(10L)
                .user(user)
                .amount(new BigDecimal("100000"))
                .provider("SEPAY")
                .transactionCode("CH_12345678")
                .status("PENDING")
                .expiresAt(expiresAt)
                .build();
    }

    private static SePayProperties validProperties() {
        SePayProperties properties = new SePayProperties();
        properties.setDepositTtlMinutes(15);
        properties.setCreateCooldownSeconds(10);
        properties.setMaxCreatesPerFifteenMinutes(5);
        properties.setMaxCreatesPerDay(20);
        return properties;
    }
}
