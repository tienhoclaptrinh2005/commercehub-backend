package com.commercehub.backend.dispute.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_WARRANTY_IN_PROGRESS = "WARRANTY_IN_PROGRESS";
    public static final String STATUS_WAITING_BUYER_CONFIRMATION = "WAITING_BUYER_CONFIRMATION";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_BUYER_WIN = "BUYER_WIN";
    public static final String STATUS_SELLER_WIN = "SELLER_WIN";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String CLOSED_REASON_BUYER_WITHDREW = "BUYER_WITHDREW";
    public static final String CLOSED_REASON_BUYER_ACCEPTED_WARRANTY = "BUYER_ACCEPTED_WARRANTY";
    public static final String CLOSED_REASON_BUYER_CONFIRMATION_TIMEOUT = "BUYER_CONFIRMATION_TIMEOUT";

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


    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    String reason;


    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "evidence_urls", columnDefinition = "TEXT[]")
    String[] evidenceUrls;

    @Column(name = "shop_response", columnDefinition = "TEXT")
    String shopResponse;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "shop_evidence_urls", columnDefinition = "TEXT[]")
    String[] shopEvidenceUrls;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30
    )
    String status = STATUS_OPEN;

    /*
     * Buyer thắng:
     * số tiền được hoàn.

     * Seller thắng:
     * có thể để 0 hoặc null.
     */
    @Column(name = "refund_amount", precision = 18, scale = 2)
    BigDecimal refundAmount;

    @Column(
            name = "admin_note",
            columnDefinition = "TEXT"
    )
    String adminNote;

    @Column(name = "resolver_id")
    Long resolverId;

    @Column(name = "closed_reason", length = 40)
    String closedReason;

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
