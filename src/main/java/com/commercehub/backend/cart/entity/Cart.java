package com.commercehub.backend.cart.entity;

import com.commercehub.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Giỏ hàng — mỗi user có đúng 1 giỏ (user_id UNIQUE).
 * Giỏ chỉ là nơi "gom hàng", KHÔNG giữ giá snapshot: giá luôn lấy
 * từ ProductVariant tại thời điểm xem giỏ / checkout.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<CartItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;
}
