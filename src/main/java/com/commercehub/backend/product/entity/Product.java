package com.commercehub.backend.product.entity;

import com.commercehub.backend.category.entity.Category;
import com.commercehub.backend.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.BatchSize;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    Category category;

    @Column(nullable = false, length = 255)
    String name;

    @Column(name = "short_description", length = 200)
    String shortDescription;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name = "product_type", nullable = false, length = 30)
    @Builder.Default
    String productType = "ACCOUNT"; // ACCOUNT, LICENSE, GIFTCARD, COOKIE, OTHER

    @Column(name = "delivery_type", nullable = false, length = 20)
    @Builder.Default
    String deliveryType = "INSTANT"; // INSTANT, PRE_ORDER

    @Column(nullable = false, length = 30)
    @Builder.Default
    String status = "ACTIVE"; // ACTIVE, INACTIVE, DELETED

    @Column(name = "sold_count", nullable = false)
    @Builder.Default
    Long soldCount = 0L;

    @Column(name = "failed_dispute_count", nullable = false)
    @Builder.Default
    Long failedDisputeCount = 0L;


    @Column(nullable = false, unique = true, length = 255)
    String slug;

    @Column(name = "thumbnail_url", length = 500)
    String thumbnailUrl;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    OffsetDateTime updatedAt;


    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @BatchSize(size = 20)
    List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ProductImage> images = new ArrayList<>();

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    PreOrderConfig preOrderConfig;
}