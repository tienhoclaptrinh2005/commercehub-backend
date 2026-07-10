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

    @Column(nullable = false, length = 20)
     String status; // HOLDING, RELEASED, REFUNDED

    @Column(name = "scheduled_release_at", nullable = false)
     OffsetDateTime scheduledReleaseAt;

    @Column(name = "released_at")
     OffsetDateTime releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
     OffsetDateTime createdAt;

    @PrePersist
    @PreUpdate
    public void validateAmounts() {
        if (holdAmount == null || feeAmount == null || sellerNetAmount == null) {
            throw new IllegalStateException("holdAmount, feeAmount, sellerNetAmount không được null.");
        }
        if (holdAmount.compareTo(feeAmount.add(sellerNetAmount)) != 0) {
            throw new IllegalStateException(
                    "Dữ liệu không hợp lệ: holdAmount phải bằng feeAmount + sellerNetAmount."
            );
        }
    }


}
