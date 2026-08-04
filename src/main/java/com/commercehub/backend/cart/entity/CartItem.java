package com.commercehub.backend.cart.entity;

import com.commercehub.backend.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * 1 dòng trong giỏ hàng.
 * Mỗi variant chỉ có 1 dòng trong 1 giỏ (unique cart + variant) —
 * thêm trùng sẽ cộng dồn quantity.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cart_item_variant", columnNames = {"cart_id", "product_variant_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    ProductVariant productVariant;

    @Column(nullable = false)
    Integer quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;
}
