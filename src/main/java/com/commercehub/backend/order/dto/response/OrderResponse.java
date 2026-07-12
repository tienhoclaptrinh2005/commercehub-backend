package com.commercehub.backend.order.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String orderCode;
    private Long shopId;
    private String shopName;
    private String deliveryType;
    private String status;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private OffsetDateTime placedAt;
}