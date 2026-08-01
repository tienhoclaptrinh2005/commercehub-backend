package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.wallet.dto.response.WalletResponse;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.WalletTransaction;
import com.commercehub.backend.wallet.mapper.WalletMapper;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletMapper walletMapper;

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        return walletMapper.toWalletResponse(wallet);
    }



    @Transactional
    public void deductBalance(Long userId, BigDecimal amount, String type, Long refId, String refType) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }


        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        assertWalletActive(wallet);

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        BigDecimal before = wallet.getAvailableBalance();
        wallet.setAvailableBalance(before.subtract(amount));
        walletRepository.save(wallet);

        logTransaction(wallet.getId(), type, "AVAILABLE", amount.negate(), before, wallet.getAvailableBalance(), refId, refType);
    }


    @Transactional
    public void holdForSeller(Long sellerId, BigDecimal amount, Long orderId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }
        Wallet wallet = walletRepository.findByUserIdWithLock(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        assertWalletActive(wallet);

        BigDecimal before = wallet.getHoldBalance();
        wallet.setHoldBalance(before.add(amount));
        walletRepository.save(wallet);

        logTransaction(wallet.getId(), "SALE_HOLD", "HOLD", amount, before, wallet.getHoldBalance(), orderId, "ORDER");
    }

    @Transactional
    public void processHoldRelease(Long sellerId, BigDecimal holdAmount, BigDecimal sellerNet, BigDecimal platformFee, Long refId) {

        if (holdAmount == null || holdAmount.compareTo(BigDecimal.ZERO) <= 0 ||
                sellerNet == null || sellerNet.compareTo(BigDecimal.ZERO) < 0 ||
                platformFee == null || platformFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        if (holdAmount.compareTo(sellerNet.add(platformFee)) != 0) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        Wallet sellerWallet = walletRepository.findByUserIdWithLock(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        assertWalletActive(sellerWallet);

        BigDecimal holdBefore = sellerWallet.getHoldBalance();

        if(holdBefore.compareTo(holdAmount) < 0){
            throw new AppException(ErrorCode.INSUFFICIENT_HOLD_BALANCE);
        }

        sellerWallet.setHoldBalance(holdBefore.subtract(holdAmount));


        BigDecimal availBefore = sellerWallet.getAvailableBalance();
        sellerWallet.setAvailableBalance(availBefore.add(sellerNet));
        walletRepository.save(sellerWallet);


        logTransaction(sellerWallet.getId(), "HOLD_RELEASE", "HOLD", holdAmount.negate(), holdBefore, sellerWallet.getHoldBalance(), refId, "HOLD_RELEASE");
        logTransaction(sellerWallet.getId(), "HOLD_RELEASE_NET", "AVAILABLE", sellerNet, availBefore, sellerWallet.getAvailableBalance(), refId, "HOLD_RELEASE");


        if (platformFee.compareTo(BigDecimal.ZERO) > 0) {
            Wallet platformWallet = walletRepository.findPlatformWalletWithLock()
                    .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
            assertWalletActive(platformWallet);
            BigDecimal platBefore = platformWallet.getAvailableBalance();
            platformWallet.setAvailableBalance(platBefore.add(platformFee));
            walletRepository.save(platformWallet);

            logTransaction(platformWallet.getId(), "PLATFORM_FEE", "AVAILABLE", platformFee, platBefore, platformWallet.getAvailableBalance(), refId, "FEE_LEDGER");
        }
    }

    @Transactional
    public void addBalance(Long userId, BigDecimal amount, String type, Long refId, String refType) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        assertWalletActive(wallet);

        BigDecimal before = wallet.getAvailableBalance();
        wallet.setAvailableBalance(before.add(amount));
        walletRepository.save(wallet);

        logTransaction(wallet.getId(), type, "AVAILABLE", amount, before, wallet.getAvailableBalance(), refId, refType);
    }

    @Transactional
    public void cancelHoldForSeller(Long sellerId, BigDecimal amount, Long refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        assertWalletActive(wallet);

        // Đảm bảo tiền đang bị hold phải lớn hơn hoặc bằng số tiền muốn gỡ
        if (wallet.getHoldBalance().compareTo(amount) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_HOLD_BALANCE);
        }

        BigDecimal before = wallet.getHoldBalance();
        wallet.setHoldBalance(before.subtract(amount));
        walletRepository.save(wallet);

        // Ghi log giao dịch (Tiền âm vì trừ đi khỏi cục Hold)
        logTransaction(wallet.getId(), "CANCEL_HOLD", "HOLD", amount.negate(), before, wallet.getHoldBalance(), refId, "ORDER_REFUND");
    }





    private void logTransaction(Long walletId, String txType, String balType, BigDecimal amount, BigDecimal before, BigDecimal after, Long refId, String refType) {
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(walletId)
                .transactionType(txType)
                .balanceType(balType)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceId(refId)
                .referenceType(refType)
                .build();
        transactionRepository.save(tx);
    }

    private void assertWalletActive(Wallet wallet) {
        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new AppException(ErrorCode.WALLET_INACTIVE);
        }
    }

}
