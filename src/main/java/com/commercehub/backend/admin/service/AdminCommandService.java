package com.commercehub.backend.admin.service;

import com.commercehub.backend.admin.dto.AdminRequests;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.auth.repository.RefreshTokenRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.shop.service.ShopService;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.entity.Withdrawal;
import com.commercehub.backend.wallet.repository.WithdrawalRepository;
import com.commercehub.backend.wallet.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminCommandService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ShopRepository shopRepository;
    private final ShopService shopService;
    private final ProductRepository productRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalService withdrawalService;
    private final AdminAuditService auditService;

    @Transactional
    public void changeUserStatus(Long actorId, Long userId, AdminRequests.StatusChange request, String ip, String agent) {
        if (actorId.equals(userId)) throw new AppException(ErrorCode.UNAUTHORIZED);
        User actor = userRepository.findByIdWithRoles(actorId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findByIdWithRoles(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (target.hasRole("SUPER_ADMIN") || (target.hasRole("ADMIN") && !actor.hasRole("SUPER_ADMIN"))) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        String status = upper(request.status());
        if (!Set.of("ACTIVE", "SUSPENDED", "BANNED").contains(status)) throw new AppException(ErrorCode.INVALID_STATUS);
        requireReasonWhenRestricted(status, request.reason());
        String oldStatus = target.getStatus();
        String oldReason = target.getBanReason();
        target.setStatus(status);
        target.setBanReason("ACTIVE".equals(status) ? null : request.reason().trim());
        userRepository.save(target);
        if (!"ACTIVE".equals(status)) refreshTokenRepository.revokeAllByUser(target);
        auditService.record(actorId, "USER_STATUS_CHANGED", "USER", userId,
                Map.of("status", oldStatus, "reason", safe(oldReason)),
                Map.of("status", status, "reason", safe(target.getBanReason())), request.reason(), ip, agent);
    }

    @Transactional
    public void changeShopStatus(Long actorId, Long shopId, AdminRequests.ShopStatusChange request, String ip, String agent) {
        Shop before = shopRepository.findById(shopId).orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (request.version() != null && !request.version().equals(before.getVersion())) {
            throw new AppException(ErrorCode.SHOP_REVIEW_CONFLICT);
        }
        String next = upper(request.status());
        if (("REJECTED".equals(next) || "BANNED".equals(next)) && !hasText(request.reason())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        String previous = before.getStatus();
        shopService.changeShopStatus(shopId, next);
        Shop after = shopRepository.findById(shopId).orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        auditService.record(actorId, "SHOP_STATUS_CHANGED", "SHOP", shopId,
                Map.of("status", previous), Map.of("status", after.getStatus()), request.reason(), ip, agent);
    }

    @Transactional
    public void changeProductStatus(Long actorId, Long productId, AdminRequests.StatusChange request, String ip, String agent) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        String next = upper(request.status());
        if (!Set.of("ACTIVE", "BANNED").contains(next)) throw new AppException(ErrorCode.INVALID_STATUS);
        if ("BANNED".equals(next) && !hasText(request.reason())) throw new AppException(ErrorCode.INVALID_REQUEST);
        if ("DELETED".equals(product.getStatus())) throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        String previous = product.getStatus();
        product.setStatus(next);
        productRepository.save(product);
        auditService.record(actorId, "PRODUCT_STATUS_CHANGED", "PRODUCT", productId,
                Map.of("status", previous, "shopId", product.getShop().getId()),
                Map.of("status", next, "shopId", product.getShop().getId()), request.reason(), ip, agent);
    }

    @Transactional
    public void processWithdrawal(Long actorId, Long withdrawalId, AdminRequests.WithdrawalDecision request, String ip, String agent) {
        Withdrawal before = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        var oldStatus = before.getStatus();
        withdrawalService.processWithdrawal(
                withdrawalId,
                actorId,
                request.action(),
                request.note(),
                request.transferReference()
        );
        Withdrawal after = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        auditService.record(actorId, "WITHDRAWAL_PROCESSED", "WITHDRAWAL", withdrawalId,
                Map.of("status", oldStatus.name()),
                Map.of(
                        "status", after.getStatus().name(),
                        "transferReference", safe(after.getTransferReference())
                ),
                request.note(), ip, agent);
    }

    private static void requireReasonWhenRestricted(String status, String reason) {
        if (!"ACTIVE".equals(status) && !hasText(reason)) throw new AppException(ErrorCode.INVALID_REQUEST);
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static String safe(String value) { return value == null ? "" : value; }
}
