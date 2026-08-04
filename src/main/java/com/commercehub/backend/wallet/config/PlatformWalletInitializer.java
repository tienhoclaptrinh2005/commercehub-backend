package com.commercehub.backend.wallet.config;

import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Đảm bảo ví PLATFORM (ví hứng phí sàn) luôn tồn tại khi ứng dụng khởi động.
 *
 * Nếu thiếu ví này, toàn bộ luồng nhả tiền T+7 sẽ fail ở bước thu phí
 * (WalletService.processHoldRelease → findPlatformWalletWithLock → WALLET_NOT_FOUND)
 * khiến seller không bao giờ nhận được tiền.
 *
 * DB nên có thêm partial unique index để chặn tạo trùng:
 *   CREATE UNIQUE INDEX uq_wallets_platform ON wallets (is_platform) WHERE is_platform = true;
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformWalletInitializer implements ApplicationRunner {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (walletRepository.existsByIsPlatformTrue()) {
            log.info("Ví platform đã tồn tại — bỏ qua khởi tạo.");
            return;
        }

        Wallet platformWallet = Wallet.builder()
                .user(null) // Ví hệ thống, không gắn với user nào
                .availableBalance(BigDecimal.ZERO)
                .holdBalance(BigDecimal.ZERO)
                .status("ACTIVE")
                .isPlatform(true)
                .build();
        walletRepository.save(platformWallet);

        log.info("Đã khởi tạo ví PLATFORM (ID={}) để hứng phí sàn.", platformWallet.getId());
    }
}
