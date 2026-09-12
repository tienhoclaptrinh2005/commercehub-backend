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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderCode;
    private Long shopId;
    private String shopName;
    private String sellerUsername;
    private String buyerUsername;
    private List<String> productNames;
    private List<String> variantNames;
    private String deliveryType;
    private OrderStatus status;
    private OrderPaymentStatus paymentStatus;
    private OrderCancelledBy cancelledBy;
    private OrderCancellationCode cancellationCode;
    private String cancellationReason;
    private OffsetDateTime cancelledAt;
    private boolean activeDispute;
    private BigDecimal totalAmount;
    private OffsetDateTime placedAt;
}
