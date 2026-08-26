package com.commercehub.backend.order.entity;

import com.commercehub.backend.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    // Snapshot dữ liệu để phòng trường hợp Shop đổi tên hoặc xóa sản phẩm sau này
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "variant_name", nullable = false, length = 100)
    private String variantName;

    @Column(name = "product_type", nullable = false, length = 30)
    private String productType;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    // =========================================================
    // SNAPSHOT PHÍ SÀN — chốt tại thời điểm buyer thanh toán.
    // Khi shop complete đơn PRE_ORDER, hệ thống dùng lại snapshot này
    // thay vì tính theo config hiện tại (admin đổi rate không ảnh hưởng
    // các đơn đã thanh toán). Bất biến: feeAmount + sellerNetAmount = lineTotal.
    // =========================================================

    @Column(name = "fee_config_id")
    private Long feeConfigId;

    @Column(name = "fee_rate_snapshot", precision = 5, scale = 4)
    private BigDecimal feeRateSnapshot;

    @Column(name = "fee_amount", precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "seller_net_amount", precision = 18, scale = 2)
    private BigDecimal sellerNetAmount;

    @Builder.Default
    @ColumnDefault("'NONE'")
    @Column(name = "refund_status", nullable = false, length = 20)
    private String refundStatus = "NONE";

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
