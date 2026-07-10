package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.wallet.entity.Deposit;
import com.commercehub.backend.wallet.repository.DepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class DepositService {

    private final DepositRepository depositRepository;
    private final WalletService walletService;

    @Transactional
    public void processSuccess(String transactionCode) {
        Deposit deposit = depositRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"PENDING".equals(deposit.getStatus())) {
            return; // Idempotency check: Tránh cộng tiền 2 lần nếu VNPay bắn IPN nhiều lần
        }

        // Cập nhật trạng thái
        deposit.setStatus("SUCCESS");
        deposit.setProcessedAt(OffsetDateTime.now());
        depositRepository.save(deposit);

        // Cộng tiền vào ví User
        walletService.addBalance(
                deposit.getUser().getId(),
                deposit.getAmount(),
                "DEPOSIT",
                deposit.getId(),
                "DEPOSIT"
        );
    }

    @Transactional
    public void processFailed(String transactionCode) {
        Deposit deposit = depositRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"PENDING".equals(deposit.getStatus())) return;

        deposit.setStatus("FAILED");
        deposit.setProcessedAt(OffsetDateTime.now());
        depositRepository.save(deposit);
    }
}