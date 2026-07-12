package com.commercehub.backend.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "order_status_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "changed_by")
    private Long changedBy; // ID user tác động, null = System tự động

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(columnDefinition = "JSONB")
    private String meta; // Dùng String lưu JSONB tạm thời, nếu project dùng thư viện Hypersistence thì đổi thành @Type(JsonType.class)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}