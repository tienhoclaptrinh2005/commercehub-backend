package com.commercehub.backend.fee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "shop_fee_summaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopFeeSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "total_sales", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_fee", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalFee = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "total_waived", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalWaived = BigDecimal.ZERO;

    @Column(name = "total_refunded", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalRefunded = BigDecimal.ZERO;

    @Column(name = "order_count", nullable = false)
    @Builder.Default
    private Integer orderCount = 0;

    @Column(name = "dispute_count", nullable = false)
    @Builder.Default
    private Integer disputeCount = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}