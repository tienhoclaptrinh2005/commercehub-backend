package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.dto.request.WithdrawalRequest;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.Withdrawal;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;


    // USER (SELLER)
    @Transactional
    public void requestWithdrawal(Long userId, WithdrawalRequest request) {

        // 1. Trừ tiền khả dụng trước (Bên trong deductBalance đã có PESSIMISTIC_WRITE lock)
        walletService.deductBalance(userId, request.getAmount(), "WITHDRAW_PENDING", null, "WITHDRAWAL");

        // 2. Lấy wallet KHÔNG lock chỉ để gán vào entity Withdrawal
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        // 3. Tạo bản ghi yêu cầu
        Withdrawal withdrawal = Withdrawal.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .fee(BigDecimal.ZERO) // Giả định hệ thống hiện tại miễn phí rút
                .bankName(request.getBankName())
                .accountNumber(request.getAccountNumber())
                .accountName(request.getAccountName())
                .status("PENDING")
                .build();

        withdrawalRepository.save(withdrawal);
    }


    //  ADMIN
    @Transactional
    public void processWithdrawal(Long withdrawalId, Long adminId, String action, String note) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"PENDING".equals(withdrawal.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if ("APPROVE".equalsIgnoreCase(action)) {
            withdrawal.setStatus("DONE");
        } else if ("REJECT".equalsIgnoreCase(action)) {
            withdrawal.setStatus("REJECTED");
            walletService.addBalance(withdrawal.getWallet().getUser().getId(), withdrawal.getAmount(), "WITHDRAW_CANCEL", withdrawal.getId(), "WITHDRAWAL");
        } else {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        withdrawal.setAdminNote(note);
        withdrawal.setProcessor(admin);
        withdrawal.setProcessedAt(OffsetDateTime.now());
        withdrawalRepository.save(withdrawal);
    }
}