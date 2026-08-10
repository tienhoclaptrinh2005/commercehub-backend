package com.commercehub.backend.product.entity;

import com.commercehub.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Entity
@Table(name = "asset_delivery_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssetDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    DigitalAsset asset;

    @Column(name = "order_item_id", nullable = false)
    Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    User buyer;

    // Bản chụp CỐ ĐỊNH nội dung đã giao — buyer mở lại đơn đọc từ đây,
    // không đọc lại digital_assets (kho có thể bị sửa/thu hồi sau khi bán).
    @Column(name = "delivery_content_snapshot", columnDefinition = "TEXT")
    String deliveryContentSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asset_data_snapshot", columnDefinition = "jsonb", nullable = false)
    String assetDataSnapshot;

    @Builder.Default
    @Column(name = "delivery_method", nullable = false, length = 30)
    String deliveryMethod = "AUTO";

    @Builder.Default
    @Column(nullable = false, length = 30)
    String status = "SUCCESS";

    @Column(name = "error_message", columnDefinition = "TEXT")
    String errorMessage;

    @CreatedDate
    @Column(name = "delivered_at", updatable = false)
    OffsetDateTime deliveredAt;
}