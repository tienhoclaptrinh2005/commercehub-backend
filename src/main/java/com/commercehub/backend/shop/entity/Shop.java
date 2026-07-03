package com.commercehub.backend.shop.entity;


import com.commercehub.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Entity
@Table(name = "shops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Shop  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
     Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false,unique = true)
     User owner;

    @Column(nullable = false, unique = true ,length = 255)
     String name;


    @Column(nullable = false, unique = true, length = 255)
     String slug;

    @Column(name = "shop_avatar_url", length = 500)
     String shopAvatarUrl;

    @Column(name = "shop_cover_url", length = 500)
     String shopCoverUrl;

    @Column(columnDefinition = "TEXT")
     String description;

    @Column(name = "total_orders", nullable = false)
    @Builder.Default
     Integer totalOrders = 0;

    @Column(name = "total_disputes", nullable = false)
    @Builder.Default
     Integer totalDisputes = 0;

    @Column(name = "dispute_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
     BigDecimal disputeRate = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    @Builder.Default
     String status = "ACTIVE";

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
     BigDecimal ratingAvg = BigDecimal.ZERO;


    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = java.time.OffsetDateTime.now();
        this.updatedAt = java.time.OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = java.time.OffsetDateTime.now();
    }
}