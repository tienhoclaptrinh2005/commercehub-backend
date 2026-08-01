package com.commercehub.backend.product.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "duration_days")
    Integer durationDays;

    @Column(nullable = false)
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá sản phẩm phải lớn hơn 0đ")
    BigDecimal price;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    Integer sortOrder = 0;

    @Column(nullable = false, length = 30)
    @Builder.Default
    String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Column(name = "stock_count", nullable = false)
    @Builder.Default
    Integer stockCount = 0;


}