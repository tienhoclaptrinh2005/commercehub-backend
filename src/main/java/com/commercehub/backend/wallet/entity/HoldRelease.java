package com.commercehub.backend.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "hold_releases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HoldRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    Wallet wallet;

    @Column(name = "order_id", nullable = false)
    Long orderId;

    @Column(name = "order_item_id", nullable = false, unique = true)
    Long orderItemId;

    @Column(name = "hold_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal holdAmount;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal feeAmount;

    @Column(name = "seller_net_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal sellerNetAmount;

    @Column(name = "fee_ledger_id")
    Long feeLedgerId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    HoldReleaseStatus status = HoldReleaseStatus.HOLDING;

    @Column(name = "scheduled_release_at", nullable = false)
    OffsetDateTime scheduledReleaseAt;

    @Column(name = "released_at")
    OffsetDateTime releasedAt;

    // =========================================================
    // COMPLAINT / WARRANTY
    // =========================================================

    @Column(name = "complaint_reason", columnDefinition = "TEXT")
    String complaintReason;

    @Column(name = "complained_at")
    OffsetDateTime complainedAt;

    @Column(name = "remaining_hold_seconds")
    Long remainingHoldSeconds;

    @Column(name = "warranty_started_at")
    OffsetDateTime warrantyStartedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    // =========================================================
    // VALIDATION
    // =========================================================

    @PrePersist
    @PreUpdate
    public void validateAmounts() {

        if (
                holdAmount == null || feeAmount == null || sellerNetAmount == null) {
            throw new IllegalStateException("holdAmount, feeAmount và sellerNetAmount không được null.");
        }

        if (holdAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("holdAmount phải lớn hơn 0.");
        }

        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("feeAmount không được nhỏ hơn 0.");
        }

        if (sellerNetAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("sellerNetAmount không được nhỏ hơn 0.");
        }

        BigDecimal expectedHoldAmount = feeAmount.add(sellerNetAmount);
        if (holdAmount.compareTo(expectedHoldAmount) != 0) {
            throw new IllegalStateException("Bất biến tài chính vi phạm: " + "holdAmount phải bằng feeAmount + sellerNetAmount."
            );
        }
    }
}
