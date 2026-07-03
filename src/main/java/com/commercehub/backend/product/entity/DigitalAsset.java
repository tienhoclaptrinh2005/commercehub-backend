package com.commercehub.backend.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "digital_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigitalAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "asset_type", nullable = false, length = 50)
    private String assetType;

    @Column(name = "asset_data", columnDefinition = "TEXT", nullable = false)
    private String assetData;

    @Column(name = "asset_identifier", length = 500)
    private String assetIdentifier;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "AVAILABLE"; // AVAILABLE, RESERVED, SOLD, DISPUTED, REVOKED

    @Column(name = "reserved_at")
    private OffsetDateTime reservedAt;

    @Column(name = "reserved_expires_at")
    private OffsetDateTime reservedExpiresAt;


    @Column(name = "order_item_id")
    private Long orderItemId;

    @Builder.Default
    @Column(name = "is_delivered", nullable = false)
    private Boolean isDelivered = false;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}