package com.commercehub.backend.dispute.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.shop.entity.Shop;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "order_disputes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_id", nullable = false)
    Long orderId;

    @Column(
            name = "order_item_id",
            nullable = false,
            unique = true
    )
    Long orderItemId;


    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "shop_id", nullable = false)
    Long shopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", insertable = false, updatable = false)
    OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", insertable = false, updatable = false)
    Shop shop;


    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    String reason;


    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "evidence_urls", columnDefinition = "TEXT[]")
    String[] evidenceUrls;

    @Column(name = "shop_response", columnDefinition = "TEXT")
    String shopResponse;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "shop_evidence_urls", columnDefinition = "TEXT[]")
    String[] shopEvidenceUrls;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30
    )
    DisputeStatus status = DisputeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 40)
    DisputeResolution resolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_by", length = 20)
    DisputeResolvedBy resolvedBy;

    /*
     * Buyer thắng:
     * số tiền được hoàn.

     * Seller thắng:
     * có thể để 0 hoặc null.
     */
    @Column(name = "refund_amount", precision = 18, scale = 2)
    BigDecimal refundAmount;

    @Column(
            name = "resolution_note",
            columnDefinition = "TEXT"
    )
    String resolutionNote;

    @Column(name = "resolver_id")
    Long resolverId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @Column(name = "deadline_at", nullable = false)
    OffsetDateTime deadlineAt;

    @Column(name = "resolved_at")
    OffsetDateTime resolvedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;
}
