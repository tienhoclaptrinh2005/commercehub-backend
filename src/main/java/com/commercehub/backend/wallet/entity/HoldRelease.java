package com.commercehub.backend.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Entity
@Table(name = "hold_releases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HoldRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    Wallet wallet;

    /**
     * ID của đơn hàng (không còn UNIQUE — 1 Order có nhiều HoldRelease tương ứng từng item).
     */
    @Column(name = "order_id", nullable = false)
    Long orderId;

    /**
     * ID của dòng sản phẩm — UNIQUE: mỗi OrderItem chỉ có đúng 1 HoldRelease.
     */
    @Column(name = "order_item_id", unique = true)
    Long orderItemId;

    @Column(name = "hold_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal holdAmount;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal feeAmount;

    @Column(name = "seller_net_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal sellerNetAmount;

    @Column(name = "fee_ledger_id")
    Long feeLedgerId;

    /**
     * Trạng thái vòng đời của khoản giữ tiền:
     * HOLDING           — Đang giam tiền, đồng hồ T+7 đang chạy.
     * COMPLAINED        — Buyer báo lỗi, đồng hồ tạm dừng, chờ Shop xử lý.
     * WARRANTY_IN_PROGRESS — Shop đang tiến hành bảo hành/đổi trả.
     * DISPUTED          — Shop từ chối, leo thang lên Admin phán xử.
     * RELEASED          — Kết thúc thành công: tiền đã nhả về ví khả dụng của Shop.
     * REFUNDED          — Kết thúc thất bại: tiền đã hoàn về ví của Buyer.
     */
    @Column(nullable = false, length = 30)
    String status;

    @Column(name = "scheduled_release_at", nullable = false)
    OffsetDateTime scheduledReleaseAt;

    @Column(name = "released_at")
    OffsetDateTime releasedAt;

    // =========================================================
    // Các trường hỗ trợ luồng Khiếu nại / Bảo hành
    // =========================================================

    /**
     * Lý do khiếu nại do Buyer nhập, lưu để Admin có thể đọc trên Dashboard.
     */
    @Column(name = "complaint_reason", columnDefinition = "TEXT")
    String complaintReason;

    /**
     * Thời điểm Buyer bấm khiếu nại → đồng hồ bắt đầu tạm dừng.
     */
    @Column(name = "complained_at")
    OffsetDateTime complainedAt;

    /**
     * Số giây còn lại trong đồng hồ T+7 tại thời điểm bị khiếu nại.
     * Dùng để tính lại scheduledReleaseAt khi bảo hành hoàn thành hoặc Admin phán Seller thắng.
     */
    @Column(name = "remaining_hold_seconds")
    Long remainingHoldSeconds;

    /**
     * Thời điểm Shop bắt đầu thực hiện bảo hành.
     */
    @Column(name = "warranty_started_at")
    OffsetDateTime warrantyStartedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @PrePersist
    @PreUpdate
    public void validateAmounts() {
        if (holdAmount == null || feeAmount == null || sellerNetAmount == null) {
            throw new IllegalStateException("holdAmount, feeAmount, sellerNetAmount không được null.");
        }
        if (holdAmount.compareTo(feeAmount.add(sellerNetAmount)) != 0) {
            throw new IllegalStateException(
                    "Bất biến tài chính vi phạm: holdAmount phải bằng feeAmount + sellerNetAmount."
            );
        }
    }
}
