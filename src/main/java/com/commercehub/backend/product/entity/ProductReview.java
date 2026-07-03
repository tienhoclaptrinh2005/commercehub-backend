package com.commercehub.backend.product.entity;

import com.commercehub.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "order_item_id", nullable = false, unique = true)
    Long orderItemId;

    @Column(nullable = false)
    Integer rating; // Giá trị từ 1 đến 5 sao

    @Column(columnDefinition = "TEXT")
    String comment;

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    Boolean isVisible = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    OffsetDateTime createdAt;

}