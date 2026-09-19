package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.dto.request.WithdrawalAction;
import com.commercehub.backend.wallet.dto.request.WithdrawalRequest;
import com.commercehub.backend.wallet.dto.response.WithdrawalResponse;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.Withdrawal;
import com.commercehub.backend.wallet.entity.WithdrawalStatus;
import com.commercehub.backend.wallet.mapper.WalletMapper;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final WalletMapper walletMapper;


    // ==========================================
    // USER (SELLER) YÊU CẦU RÚT TIỀN
    // ==========================================
    @Transactional
    public WithdrawalResponse requestWithdrawal(Long userId, WithdrawalRequest request) {

        // 1. ĐÃ SỬA THÀNH findByUserIdWithLock ĐỂ ÉP HIBERNATE KHÔNG DÙNG L1 CACHE
        // Ngăn chặn triệt để hành vi spam request để rút vượt số dư.
        // Lock ví cũng serialize các request song song của cùng user →
        // idempotency check bên dưới an toàn với race.
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        // 2. Idempotency: client retry cùng key → không tạo đơn rút mới, không trừ ví lần 2
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = withdrawalRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existing.isPresent()) {
                Withdrawal saved = existing.get();
                if (!saved.getWallet().getUser().getId().equals(userId)
                        || saved.getAmount().compareTo(request.getAmount()) != 0
                        || !saved.getBankName().equalsIgnoreCase(request.getBankName().trim())
                        || !saved.getAccountNumber().equals(request.getAccountNumber().trim())
                        || !saved.getAccountName().equalsIgnoreCase(request.getAccountName().trim())) {
                    throw new AppException(ErrorCode.WITHDRAWAL_IDEMPOTENCY_CONFLICT);
                }
                // Idempotency keys are request credentials and must not appear in deployment logs.
                log.info("Withdrawal idempotency hit — user {} → đơn rút ID {}",
                        userId, saved.getId());
                return walletMapper.toWithdrawalResponse(saved);
            }
        }

        // 3. Tạo bản ghi yêu cầu VÀ LƯU TRƯỚC để DB sinh ra ID
        Withdrawal withdrawal = Withdrawal.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .fee(BigDecimal.ZERO) // Giả định hệ thống hiện tại miễn phí rút
                .bankName(request.getBankName().trim())
                .accountNumber(request.getAccountNumber().trim())
                .accountName(request.getAccountName().trim().toUpperCase(java.util.Locale.ROOT))
                .idempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim())
                .status(WithdrawalStatus.PENDING)
                .build();

        withdrawal = withdrawalRepository.save(withdrawal); // Nhận lại Entity đã có ID

        // 4. Trừ tiền khả dụng và TRUYỀN ID CỦA ĐƠN RÚT TIỀN VÀO LỊCH SỬ GIAO DỊCH
        walletService.deductBalance(
                userId,
                request.getAmount(),
                "WITHDRAW_PENDING",
                withdrawal.getId(),
                "WITHDRAWAL"
        );

        log.info("User {} đã tạo yêu cầu rút tiền thành công. Withdrawal ID: {}", userId, withdrawal.getId());
        return walletMapper.toWithdrawalResponse(withdrawal);
    }

    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> getMyWithdrawals(Long userId, int page, int size) {
        Page<WithdrawalResponse> result = withdrawalRepository
                .findByWalletUserIdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))
                )
                .map(walletMapper::toWithdrawalResponse);
        return PageResponse.of(result);
    }


    // ==========================================
    // ADMIN DUYỆT / TỪ CHỐI RÚT TIỀN
    // ==========================================
    @Transactional
    public void processWithdrawal(
            Long withdrawalId,
            Long adminId,
            WithdrawalAction action,
            String note,
            String transferReference
    ) {

        // ĐÃ ĐỔI THÀNH findByIdWithLock ĐỂ CHỐNG ADMIN CLICK ĐÚP GÂY NHÂN ĐÔI TIỀN HOÀN
        Withdrawal withdrawal = withdrawalRepository.findByIdWithLock(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (action == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now();
        switch (action) {
            case APPROVE -> {
                requireStatus(withdrawal, WithdrawalStatus.PENDING);
                withdrawal.setStatus(WithdrawalStatus.APPROVED);
                withdrawal.setApprovedBy(admin);
                withdrawal.setApprovedAt(now);
                setNoteWhenPresent(withdrawal, note);
                log.info("Admin {} đã TIẾP NHẬN đơn rút tiền ID {}", adminId, withdrawalId);
            }
            case COMPLETE -> {
                requireStatus(withdrawal, WithdrawalStatus.APPROVED);
                if (!hasText(transferReference)) {
                    throw new AppException(ErrorCode.WITHDRAWAL_TRANSFER_REFERENCE_REQUIRED);
                }
                withdrawal.setStatus(WithdrawalStatus.DONE);
                withdrawal.setTransferReference(transferReference.trim());
                withdrawal.setProcessor(admin);
                withdrawal.setProcessedAt(now);
                setNoteWhenPresent(withdrawal, note);
                walletService.recordBalanceEvent(
                        withdrawal.getWallet().getUser().getId(),
                        "WITHDRAW_DONE",
                        withdrawal.getId(),
                        "WITHDRAWAL",
                        "Ngân hàng đã xác nhận chuyển khoản rút tiền"
                );
                log.info("Admin {} đã HOÀN TẤT đơn rút tiền ID {}", adminId, withdrawalId);
            }
            case REJECT -> {
                if (withdrawal.getStatus() != WithdrawalStatus.PENDING
                        && withdrawal.getStatus() != WithdrawalStatus.APPROVED) {
                    throw new AppException(ErrorCode.WITHDRAWAL_INVALID_TRANSITION);
                }
                if (!hasText(note)) {
                    throw new AppException(ErrorCode.WITHDRAWAL_REJECTION_REASON_REQUIRED);
                }
                withdrawal.setStatus(WithdrawalStatus.REJECTED);
                withdrawal.setAdminNote(note.trim());
                withdrawal.setProcessor(admin);
                withdrawal.setProcessedAt(now);
                walletService.systemCreditBalance(
                        withdrawal.getWallet().getUser().getId(),
                        withdrawal.getAmount(),
                        "WITHDRAW_CANCEL",
                        withdrawal.getId(),
                        "WITHDRAWAL"
                );
                log.info("Admin {} đã TỪ CHỐI đơn rút tiền ID {}", adminId, withdrawalId);
            }
        }

        withdrawalRepository.save(withdrawal);
    }

    private void requireStatus(Withdrawal withdrawal, WithdrawalStatus expected) {
        if (withdrawal.getStatus() != expected) {
            throw new AppException(ErrorCode.WITHDRAWAL_INVALID_TRANSITION);
        }
    }

    private void setNoteWhenPresent(Withdrawal withdrawal, String note) {
        if (hasText(note)) {
            withdrawal.setAdminNote(note.trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
