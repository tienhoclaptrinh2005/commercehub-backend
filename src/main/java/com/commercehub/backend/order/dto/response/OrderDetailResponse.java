package com.commercehub.backend.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.commercehub.backend.order.entity.OrderCancellationCode;
import com.commercehub.backend.order.entity.OrderCancelledBy;
import com.commercehub.backend.order.entity.OrderPaymentStatus;
import com.commercehub.backend.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor

@Builder
public class OrderDetailResponse {
    private Long id;
    private String orderCode;
    private Long shopId;
    private String shopName;
    private String sellerUsername;
    private String buyerUsername;
    private String deliveryType;
    private OrderStatus status;
    private OrderPaymentStatus paymentStatus;
    private OrderCancelledBy cancelledBy;
    private OrderCancellationCode cancellationCode;
    private String cancellationReason;
    private OffsetDateTime cancelledAt;
    private boolean activeDispute;
    private String paymentMethod;
    private BigDecimal subtotalAmount;
    private BigDecimal voucherDiscount;
    private BigDecimal totalAmount;
    private OffsetDateTime placedAt;
    private OffsetDateTime approvalDeadlineAt;
    private OffsetDateTime processingDeadlineAt;
    private String rejectionReason;
    private OffsetDateTime deliveredAt;

    // Danh sách sản phẩm trong đơn
    private List<OrderItemResponse> items;
    List<OrderStatusLogResponse> statusLogs;
}
