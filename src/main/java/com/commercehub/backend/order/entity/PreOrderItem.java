package com.commercehub.backend.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pre_order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @Column(name = "buyer_inputs", columnDefinition = "TEXT")
    private String buyerInputs;

    // Nội dung shop giao cho khách (account/key/tin nhắn) — buyer xem lại vĩnh viễn từ đây.
    // KHÔNG dùng sellerNotes để giao hàng (sellerNotes chỉ là ghi chú nội bộ).
    @Column(name = "delivery_content", columnDefinition = "TEXT")
    private String deliveryContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_content_type", length = 20)
    private DeliveryContentType deliveryContentType;

    @Column(name = "seller_notes", columnDefinition = "TEXT")
    private String sellerNotes;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
