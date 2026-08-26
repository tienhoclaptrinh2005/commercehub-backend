package com.commercehub.backend.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idempotency_user_operation_key",
                columnNames = {"user_id", "operation_type", "key_value"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutAttempt {

    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_value", nullable = false, length = 100)
    private String keyValue;

    @Column(name = "operation_type", nullable = false, length = 100)
    private String operationType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PROCESSING";

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void validateRequestHash() {
        if (requestHash == null
                || !SHA_256_HEX.matcher(requestHash).matches()
                || requestHash.chars().allMatch(character -> character == '0')) {
            throw new IllegalStateException("requestHash phải là SHA-256 hợp lệ của payload checkout");
        }
    }
}
