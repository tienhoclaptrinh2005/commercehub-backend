package com.commercehub.backend.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO chỉ đọc cho portal quản trị. Giữ contract admin tập trung và rõ trên OpenAPI. */
public final class AdminResponses {
    private AdminResponses() {}

    public record Dashboard(
            long totalUsers,
            long activeSellers,
            long pendingShops,
            long activeShops,
            long activeProducts,
            long ordersThisMonth,
            long openDisputes,
            BigDecimal disputeRate,
            long pendingWithdrawals,
            long depositsNeedReview,
            BigDecimal gmvThisMonth,
            BigDecimal collectedFeesThisMonth,
            BigDecimal totalCollectedFees,
            BigDecimal totalHoldBalance,
            List<DailyMetric> dailyMetrics,
            List<LeaderboardItem> topShops,
            List<LeaderboardItem> topCategories
    ) {}

    public record DailyMetric(LocalDate date, BigDecimal gmv, BigDecimal fee, long orderCount) {}

    public record LeaderboardItem(Long id, String name, BigDecimal amount, long orderCount) {}

    public record UserRow(
            Long id, String email, String username, String fullName, String avatarUrl,
            String status, String banReason, String role, String provider, Integer level,
            BigDecimal accumulatedSpent, BigDecimal accumulatedEarned,
            boolean emailVerified, boolean phoneVerified,
            OffsetDateTime createdAt, OffsetDateTime lastActiveAt
    ) {}

    public record ShopRow(
            Long id, Long ownerId, String ownerEmail, String ownerUsername,
            String name, String slug, String avatarUrl, String status,
            long productCount, long soldCount, int totalOrders, int totalDisputes,
            BigDecimal disputeRate, BigDecimal ratingAvg, long ratingCount,
            long version, OffsetDateTime createdAt
    ) {}

    public record ProductRow(
            Long id, Long shopId, String shopName, Long categoryId, String categoryName,
            String name, String slug, String thumbnailUrl, String productType,
            String deliveryType, String status, long soldCount, long stockCount,
            BigDecimal minPrice, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    public record CategoryRow(
            Long id, String name, String slug, String iconUrl, boolean active,
            int sortOrder, Long parentId, String parentName, long productCount
    ) {}

    public record DepositRow(
            Long id, Long userId, String username, String email, BigDecimal amount,
            String provider, String transactionCode, String providerTransactionId,
            String status, OffsetDateTime expiresAt, OffsetDateTime paidAt,
            OffsetDateTime processedAt, OffsetDateTime createdAt
    ) {}

    public record WithdrawalRow(
            Long id, Long userId, String username, String email, BigDecimal amount,
            BigDecimal fee, String bankName, String accountNumber, String accountName,
            String status, String adminNote, String processorUsername,
            OffsetDateTime processedAt, OffsetDateTime createdAt
    ) {}

    public record WalletTransactionRow(
            UUID id, Long walletId, Long userId, String username, String email,
            String transactionType, String balanceType, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter,
            Long referenceId, String referenceType, String referenceCode,
            String description, OffsetDateTime createdAt
    ) {}

    public record AuditLogRow(
            Long id, Long actorId, String actorUsername, String actorRole,
            String action, String targetType, Long targetId,
            Map<String, Object> oldValue, Map<String, Object> newValue,
            String reason, String ipAddress, String userAgent, OffsetDateTime createdAt
    ) {}
}
