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


    // ==========================================
    // USER (SELLER) YÊU CẦU RÚT TIỀN
    // ==========================================
    @Transactional
    public void requestWithdrawal(Long userId, WithdrawalRequest request) {

        // 1. ĐÃ SỬA THÀNH findByUserIdWithLock ĐỂ ÉP HIBERNATE KHÔNG DÙNG L1 CACHE
        // Ngăn chặn triệt để hành vi spam request để rút vượt số dư.
        // Lock ví cũng serialize các request song song của cùng user →
        // idempotency check bên dưới an toàn với race.
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        // 2. Idempotency: client retry cùng key → không tạo đơn rút mới, không trừ ví lần 2
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = withdrawalRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Withdrawal idempotency hit — user {} key {} → đơn rút ID {}",
                        userId, idempotencyKey, existing.get().getId());
                return;
            }
        }

        // 3. Tạo bản ghi yêu cầu VÀ LƯU TRƯỚC để DB sinh ra ID
        Withdrawal withdrawal = Withdrawal.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .fee(BigDecimal.ZERO) // Giả định hệ thống hiện tại miễn phí rút
                .bankName(request.getBankName())
                .accountNumber(request.getAccountNumber())
                .accountName(request.getAccountName())
                .idempotencyKey(idempotencyKey)
                .status("PENDING")
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
    }


    // ==========================================
    // ADMIN DUYỆT / TỪ CHỐI RÚT TIỀN
    // ==========================================
    @Transactional
    public void processWithdrawal(Long withdrawalId, Long adminId, String action, String note) {

        // ĐÃ ĐỔI THÀNH findByIdWithLock ĐỂ CHỐNG ADMIN CLICK ĐÚP GÂY NHÂN ĐÔI TIỀN HOÀN
        Withdrawal withdrawal = withdrawalRepository.findByIdWithLock(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"PENDING".equals(withdrawal.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATUS); // Request thứ 2 sẽ bị văng lỗi ở đây ngay
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if ("APPROVE".equalsIgnoreCase(action)) {
            withdrawal.setStatus("DONE");
            log.info("Admin {} đã DUYỆT đơn rút tiền ID {}", adminId, withdrawalId);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            withdrawal.setStatus("REJECTED");
            // Hoàn lại tiền vào ví do bị từ chối rút
            walletService.systemCreditBalance(
                    withdrawal.getWallet().getUser().getId(),
                    withdrawal.getAmount(),
                    "WITHDRAW_CANCEL",
                    withdrawal.getId(),
                    "WITHDRAWAL"
            );
            log.info("Admin {} đã TỪ CHỐI đơn rút tiền ID {}", adminId, withdrawalId);
        } else {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        withdrawal.setAdminNote(note);
        withdrawal.setProcessor(admin);
        withdrawal.setProcessedAt(OffsetDateTime.now());
        withdrawalRepository.save(withdrawal);
    }
}
