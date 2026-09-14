package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.dto.request.WithdrawalAction;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.Withdrawal;
import com.commercehub.backend.wallet.entity.WithdrawalStatus;
import com.commercehub.backend.wallet.mapper.WalletMapper;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.repository.WithdrawalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock WithdrawalRepository withdrawalRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletService walletService;
    @Mock UserRepository userRepository;
    @Mock WalletMapper walletMapper;
    @InjectMocks WithdrawalService service;

    @Test
    void approveMovesPendingRequestToApprovedWithoutChangingBalance() {
        Withdrawal withdrawal = withdrawal(WithdrawalStatus.PENDING);
        User admin = User.builder().id(90L).build();
        stub(withdrawal, admin);

        service.processWithdrawal(10L, 90L, WithdrawalAction.APPROVE, "Đã kiểm tra", null);

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(withdrawal.getApprovedBy()).isSameAs(admin);
        assertThat(withdrawal.getApprovedAt()).isNotNull();
        assertThat(withdrawal.getProcessedAt()).isNull();
        verify(walletService, never()).systemCreditBalance(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void completeMovesApprovedRequestToDoneAndRecordsTerminalEvent() {
        Withdrawal withdrawal = withdrawal(WithdrawalStatus.APPROVED);
        User admin = User.builder().id(90L).build();
        stub(withdrawal, admin);

        service.processWithdrawal(10L, 90L, WithdrawalAction.COMPLETE, null, "BANK-123456");

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.DONE);
        assertThat(withdrawal.getTransferReference()).isEqualTo("BANK-123456");
        assertThat(withdrawal.getProcessor()).isSameAs(admin);
        assertThat(withdrawal.getProcessedAt()).isNotNull();
        verify(walletService).recordBalanceEvent(
                7L, "WITHDRAW_DONE", 10L, "WITHDRAWAL",
                "Ngân hàng đã xác nhận chuyển khoản rút tiền");
    }

    @Test
    void rejectRefundsApprovedRequestExactlyOnce() {
        Withdrawal withdrawal = withdrawal(WithdrawalStatus.APPROVED);
        User admin = User.builder().id(90L).build();
        stub(withdrawal, admin);

        service.processWithdrawal(10L, 90L, WithdrawalAction.REJECT, "Sai tên chủ tài khoản", null);

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
        assertThat(withdrawal.getAdminNote()).isEqualTo("Sai tên chủ tài khoản");
        verify(walletService).systemCreditBalance(
                7L, new BigDecimal("500000"), "WITHDRAW_CANCEL", 10L, "WITHDRAWAL");

        assertThatThrownBy(() -> service.processWithdrawal(
                10L, 90L, WithdrawalAction.REJECT, "Thử lại", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_INVALID_TRANSITION);
    }

    @Test
    void rejectRequiresReasonAndCompleteRequiresTransferReference() {
        User admin = User.builder().id(90L).build();
        Withdrawal pending = withdrawal(WithdrawalStatus.PENDING);
        stub(pending, admin);

        assertThatThrownBy(() -> service.processWithdrawal(
                10L, 90L, WithdrawalAction.REJECT, " ", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_REJECTION_REASON_REQUIRED);
        assertThat(pending.getStatus()).isEqualTo(WithdrawalStatus.PENDING);

        Withdrawal approved = withdrawal(WithdrawalStatus.APPROVED);
        when(withdrawalRepository.findByIdWithLock(11L)).thenReturn(Optional.of(approved));
        assertThatThrownBy(() -> service.processWithdrawal(
                11L, 90L, WithdrawalAction.COMPLETE, null, " "))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_TRANSFER_REFERENCE_REQUIRED);
        assertThat(approved.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
    }

    private void stub(Withdrawal withdrawal, User admin) {
        when(withdrawalRepository.findByIdWithLock(withdrawal.getId())).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
    }

    private Withdrawal withdrawal(WithdrawalStatus status) {
        User seller = User.builder().id(7L).build();
        Wallet wallet = Wallet.builder().id(5L).user(seller).build();
        return Withdrawal.builder()
                .id(10L)
                .wallet(wallet)
                .amount(new BigDecimal("500000"))
                .fee(BigDecimal.ZERO)
                .bankName("Agribank")
                .accountNumber("123456789")
                .accountName("NGUYEN VAN A")
                .status(status)
                .build();
    }
}
