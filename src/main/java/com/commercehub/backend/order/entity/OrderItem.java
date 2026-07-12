package com.commercehub.backend.order.entity;

import com.commercehub.backend.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}