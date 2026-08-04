package com.commercehub.backend.fee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_fee_ledgers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformFeeLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false, unique = true)
    private Long orderItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "seller_wallet_id", nullable = false)
    private Long sellerWalletId;

    @Column(name = "fee_config_id", nullable = false)
    private Long feeConfigId;

    @Column(name = "fee_rate_snapshot", nullable = false, precision = 5, scale = 4)
    private BigDecimal feeRateSnapshot;

    @Column(name = "sale_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal saleAmount;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "seller_net_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal sellerNetAmount;

    @Column(name = "adjusted_sale_amount", precision = 18, scale = 2)
    private BigDecimal adjustedSaleAmount;

    @Column(name = "adjusted_fee_amount", precision = 18, scale = 2)
    private BigDecimal adjustedFeeAmount;

    @Column(name = "adjusted_seller_net", precision = 18, scale = 2)
    private BigDecimal adjustedSellerNet;

    @Column(name = "adjustment_reason", columnDefinition = "TEXT")
    private String adjustmentReason;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, COLLECTED, CANCELLED

    @Column(name = "fee_incurred_at", nullable = false)
    private OffsetDateTime feeIncurredAt;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "hold_release_id")
    private Long holdReleaseId;

    @Column(name = "dispute_id")
    private Long disputeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}