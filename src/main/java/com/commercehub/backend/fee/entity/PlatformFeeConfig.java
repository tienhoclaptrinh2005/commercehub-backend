package com.commercehub.backend.fee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_fee_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformFeeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal feeRate;

    @Column(name = "min_fee_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal minFeeAmount = BigDecimal.ZERO;

    @Column(name = "max_fee_amount", precision = 18, scale = 2)
    private BigDecimal maxFeeAmount;

    @Column(length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private OffsetDateTime effectiveFrom = OffsetDateTime.now();

    @Column(name = "effective_until")
    private OffsetDateTime effectiveUntil;

    @Column(name = "created_by", nullable = false)
    private Long createdBy; // ID của Admin tạo

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}