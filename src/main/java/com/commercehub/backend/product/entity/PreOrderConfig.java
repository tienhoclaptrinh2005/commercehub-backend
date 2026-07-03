package com.commercehub.backend.product.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pre_order_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreOrderConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    Product product;

    @Column(name = "max_processing_hours", nullable = false)
    @Builder.Default
    Integer maxProcessingHours = 24;


    @Column(name = "order_instructions", columnDefinition = "TEXT")
    String orderInstructions;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buyer_input_fields", columnDefinition = "jsonb")
    String buyerInputFields;

    @Column(name = "auto_reject_if_unavailable", nullable = false)
    @Builder.Default
    Boolean autoRejectIfUnavailable = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    OffsetDateTime updatedAt;


}
