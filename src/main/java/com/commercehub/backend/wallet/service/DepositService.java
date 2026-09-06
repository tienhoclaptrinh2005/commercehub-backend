package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.wallet.config.SePayProperties;
import com.commercehub.backend.wallet.dto.request.DepositRequest;
import com.commercehub.backend.wallet.dto.response.DepositQrResponse;
import com.commercehub.backend.wallet.dto.response.DepositResponse;
import com.commercehub.backend.wallet.entity.Deposit;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.DepositRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositService {

    private static final int MAX_HISTORY_PAGE_SIZE = 50;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final DepositRepository depositRepository;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final VietQrService vietQrService;
    private final SePayProperties sePayProperties;

    @Transactional(readOnly = true)
    public PageResponse<DepositResponse> getMyDeposits(Long userId, int page, int size) {
        if (page < 1 || size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Page<DepositResponse> deposits = depositRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, size))
                .map(this::toHistoryResponse);

        return PageResponse.of(deposits);
    }

    @Transactional
    public DepositQrResponse createDeposit(Long userId, DepositRequest request) {
        vietQrService.validateConfiguration();
        OffsetDateTime now = OffsetDateTime.now();

        // Lock ví để hai request tạo QR đồng thời của cùng user không thể tạo hai PENDING.
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        Deposit idempotentDeposit = depositRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .orElse(null);
        if (idempotentDeposit != null) {
            if (!idempotentDeposit.getUser().getId().equals(userId)
                    || idempotentDeposit.getAmount().compareTo(request.getAmount()) != 0) {
                throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            expireIfNecessary(idempotentDeposit, now);
            return toQrResponse(idempotentDeposit);
        }

        Deposit activeDeposit = depositRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING")
                .orElse(null);
        if (activeDeposit != null) {
            expireIfNecessary(activeDeposit, now);
            if ("PENDING".equals(activeDeposit.getStatus())) {
                if (activeDeposit.getAmount().compareTo(request.getAmount()) == 0) {
                    return toQrResponse(activeDeposit);
                }
                throw new AppException(ErrorCode.DEPOSIT_ACTIVE_EXISTS);
            }
        }

        enforceCreateRateLimits(userId, now);

        String transactionCode = generateUniqueTransactionCode();
        Deposit deposit = Deposit.builder()
                .user(wallet.getUser())
                .wallet(wallet)
                .amount(request.getAmount())
                .provider("SEPAY")
                .transactionCode(transactionCode)
                .idempotencyKey(request.getIdempotencyKey())
                .status("PENDING")
                .expiresAt(now.plusMinutes(sePayProperties.getDepositTtlMinutes()))
                .build();

        depositRepository.saveAndFlush(deposit);
        return toQrResponse(deposit);
    }

    @Transactional
    public DepositQrResponse getMyDepositStatus(Long userId, String transactionCode) {
        Deposit deposit = depositRepository.findByTransactionCodeAndUserId(transactionCode, userId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        expireIfNecessary(deposit, OffsetDateTime.now());
        return toQrResponse(deposit);
    }

    @Transactional
    public void processBankTransfer(
            String transactionCode,
            BigDecimal paidAmount,
            String providerTransactionId,
            OffsetDateTime paidAt) {
        // Duy trì thứ tự lock wallet -> deposit giống luồng tạo QR để tránh deadlock.
        Deposit candidate = depositRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        walletRepository.findByUserIdWithLock(candidate.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        Deposit deposit = depositRepository.findByTransactionCodeWithLock(transactionCode)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if ("SUCCESS".equals(deposit.getStatus()) || "REVIEW_REQUIRED".equals(deposit.getStatus())) {
            if (!providerTransactionId.equals(deposit.getProviderTransactionId())) {
                log.warn("Deposit {} nhận thêm giao dịch SePay {} sau khi đã xử lý",
                        transactionCode, providerTransactionId);
            }
            return;
        }
        if (!"PENDING".equals(deposit.getStatus()) && !"EXPIRED".equals(deposit.getStatus())) {
            return;
        }
        if (depositRepository.existsByProviderTransactionId(providerTransactionId)) {
            log.warn("Bỏ qua giao dịch SePay {} đã được xử lý trước đó", providerTransactionId);
            return;
        }

        boolean exactAmount = paidAmount != null && deposit.getAmount().compareTo(paidAmount) == 0;
        boolean paidBeforeExpiry = paidAt != null && !paidAt.isAfter(deposit.getExpiresAt());

        deposit.setProviderTransactionId(providerTransactionId);
        deposit.setPaidAt(paidAt);
        deposit.setProcessedAt(OffsetDateTime.now());

        if (!exactAmount || !paidBeforeExpiry) {
            deposit.setStatus("REVIEW_REQUIRED");
            depositRepository.saveAndFlush(deposit);
            log.warn("Deposit {} cần đối soát: exactAmount={}, paidBeforeExpiry={}",
                    transactionCode, exactAmount, paidBeforeExpiry);
            return;
        }

        deposit.setStatus("SUCCESS");
        depositRepository.saveAndFlush(deposit);
        walletService.systemCreditBalance(
                deposit.getUser().getId(),
                deposit.getAmount(),
                "DEPOSIT",
                deposit.getId(),
                "DEPOSIT"
        );
    }

    @Transactional
    public int expirePendingDeposits() {
        return depositRepository.expirePendingDeposits(OffsetDateTime.now());
    }

    private void enforceCreateRateLimits(Long userId, OffsetDateTime now) {
        Deposit latest = depositRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (latest != null && latest.getCreatedAt() != null
                && Duration.between(latest.getCreatedAt(), now).getSeconds()
                < sePayProperties.getCreateCooldownSeconds()) {
            throw new AppException(ErrorCode.DEPOSIT_CREATE_TOO_FAST);
        }

        long recentCount = depositRepository.countByUserIdAndCreatedAtAfter(userId, now.minusMinutes(15));
        if (recentCount >= sePayProperties.getMaxCreatesPerFifteenMinutes()) {
            throw new AppException(ErrorCode.DEPOSIT_RATE_LIMITED);
        }

        long dailyCount = depositRepository.countByUserIdAndCreatedAtAfter(userId, now.minusHours(24));
        if (dailyCount >= sePayProperties.getMaxCreatesPerDay()) {
            throw new AppException(ErrorCode.DEPOSIT_DAILY_LIMIT_REACHED);
        }
    }

    private void expireIfNecessary(Deposit deposit, OffsetDateTime now) {
        if ("PENDING".equals(deposit.getStatus()) && !deposit.getExpiresAt().isAfter(now)) {
            deposit.setStatus("EXPIRED");
            deposit.setProcessedAt(now);
            depositRepository.saveAndFlush(deposit);
        }
    }

    private DepositQrResponse toQrResponse(Deposit deposit) {
        String paymentCode = toPaymentCode(deposit.getTransactionCode());
        return DepositQrResponse.builder()
                .id(deposit.getId())
                .transactionCode(deposit.getTransactionCode())
                .paymentCode(paymentCode)
                .amount(deposit.getAmount())
                .status(deposit.getStatus())
                .qrUrl(vietQrService.buildQrUrl(deposit.getAmount(), paymentCode))
                .bankCode(vietQrService.getBankCode())
                .bankAccountNumber(vietQrService.getBankAccountNumber())
                .accountName(vietQrService.getAccountName())
                .expiresAt(deposit.getExpiresAt())
                .paidAt(deposit.getPaidAt())
                .build();
    }

    private DepositResponse toHistoryResponse(Deposit deposit) {
        return DepositResponse.builder()
                .id(deposit.getId())
                .amount(deposit.getAmount())
                .provider(deposit.getProvider())
                .transactionCode(deposit.getTransactionCode())
                .status(deposit.getStatus())
                .expiresAt(deposit.getExpiresAt())
                .paidAt(deposit.getPaidAt())
                .processedAt(deposit.getProcessedAt())
                .createdAt(deposit.getCreatedAt())
                .build();
    }

    private String generateUniqueTransactionCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = "CH_" + String.format("%08d", SECURE_RANDOM.nextInt(100_000_000));
            if (!depositRepository.existsByTransactionCode(code)) {
                return code;
            }
        }
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    public static String toInternalTransactionCode(String paymentCode) {
        if (paymentCode == null || !paymentCode.matches("(?i)^CH\\d{8}$")) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_NOTIFICATION);
        }
        return "CH_" + paymentCode.substring(2);
    }

    private static String toPaymentCode(String transactionCode) {
        if (transactionCode == null || !transactionCode.matches("^CH_\\d{8}$")) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_NOTIFICATION);
        }
        return "CH" + transactionCode.substring(3);
    }
}
