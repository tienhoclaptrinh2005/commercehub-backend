package com.commercehub.backend.order.entity;

import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Người mua

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop; // Gian hàng bán

    @Column(name = "cart_session_id", length = 100)
    private String cartSessionId;

    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "voucher_discount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal voucherDiscount = BigDecimal.ZERO;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType; // INSTANT, PRE_ORDER

    @Column(nullable = false, length = 30)
    private String status; // PENDING, PROCESSING, DELIVERED, WAITING_APPROVAL, APPROVED, REJECTED, DISPUTED, REFUNDED, CANCELLED

    @Column(name = "payment_status", nullable = false, length = 30)
    private String paymentStatus; // UNPAID, PAID, REFUNDED, PARTIAL_REFUND

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod; // Đơn mua hàng hiện chỉ thanh toán bằng WALLET.

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "placed_at")
    private OffsetDateTime placedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "approval_deadline_at")
    private OffsetDateTime approvalDeadlineAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * Idempotency key do client gửi kèm checkout — chống double-submit tạo
     * 2 đơn và trừ ví 2 lần. Nhiều order có thể chung 1 key (1 lần checkout
     * tách thành nhiều đơn theo shop/loại giao hàng).
     */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "checkout_request_id")
    private Long checkoutRequestId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "processing_deadline_at")
    private OffsetDateTime processingDeadlineAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


}
