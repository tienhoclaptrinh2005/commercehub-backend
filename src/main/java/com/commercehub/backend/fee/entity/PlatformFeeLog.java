package com.commercehub.backend.fee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_fee_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformFeeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fee_ledger_id", nullable = false)
    private Long feeLedgerId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "changed_by")
    private Long changedBy; // ID user tác động, null = System

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "JSONB")
    private String meta;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}