package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.dto.response.DepositResponse;
import com.commercehub.backend.wallet.entity.Deposit;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.DepositRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class DepositService {

    private static final int MAX_HISTORY_PAGE_SIZE = 50;

    private final DepositRepository depositRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public PageResponse<DepositResponse> getMyDeposits(Long userId, int page, int size) {
        if (page < 1 || size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Page<DepositResponse> deposits = depositRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, size))
                .map(deposit -> DepositResponse.builder()
                        .id(deposit.getId())
                        .amount(deposit.getAmount())
                        .provider(deposit.getProvider())
                        .transactionCode(deposit.getTransactionCode())
                        .status(deposit.getStatus())
                        .processedAt(deposit.getProcessedAt())
                        .createdAt(deposit.getCreatedAt())
                        .build());

        return PageResponse.of(deposits);
    }

    // ==========================================
    // TẠO ĐƠN NẠP TIỀN (PENDING)
    // ==========================================
    @Transactional
    public void createPendingDeposit(Long userId, BigDecimal amount, String txCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        Deposit deposit = Deposit.builder()
                .user(user)
                .wallet(wallet)
                .amount(amount)
                .provider("VNPAY")
                .transactionCode(txCode)
                .status("PENDING")
                .build();

        depositRepository.save(deposit);
    }

    // ==========================================
    // VNPay TRẢ VỀ THÀNH CÔNG
    // ==========================================
    @Transactional
    public void processSuccess(String transactionCode, BigDecimal paidAmount) {
        // ĐÃ SỬA THÀNH findByTransactionCodeWithLock ĐỂ TRÁNH LỖI NHÂN ĐÔI TIỀN
        Deposit deposit = depositRepository.findByTransactionCodeWithLock(transactionCode)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"PENDING".equals(deposit.getStatus())) {
            return; // Idempotency check: Tránh cộng tiền 2 lần nếu VNPay bắn IPN nhiều lần
        }

        // ĐỐI CHIẾU SỐ TIỀN: chỉ cộng ví đúng số tiền user đã đăng ký nạp.
        // Lệch số tiền → giữ nguyên PENDING để admin đối soát thủ công.
        if (paidAmount == null || deposit.getAmount().compareTo(paidAmount) != 0) {
            throw new AppException(ErrorCode.DEPOSIT_AMOUNT_MISMATCH);
        }

        // Cập nhật trạng thái
        deposit.setStatus("SUCCESS");
        deposit.setProcessedAt(OffsetDateTime.now());
        depositRepository.save(deposit);

        // Cộng tiền vào ví User
        walletService.systemCreditBalance(
                deposit.getUser().getId(),
                deposit.getAmount(),
                "DEPOSIT",
                deposit.getId(),
                "DEPOSIT"
        );
    }

    // ==========================================
    // VNPay TRẢ VỀ THẤT BẠI
    // ==========================================
    @Transactional
    public void processFailed(String transactionCode) {
        // ĐÃ SỬA THÀNH findByTransactionCodeWithLock
        Deposit deposit = depositRepository.findByTransactionCodeWithLock(transactionCode)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"PENDING".equals(deposit.getStatus())) return;

        deposit.setStatus("FAILED");
        deposit.setProcessedAt(OffsetDateTime.now());
        depositRepository.save(deposit);
    }
}
