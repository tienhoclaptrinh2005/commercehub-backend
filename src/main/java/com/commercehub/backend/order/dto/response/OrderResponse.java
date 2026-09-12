package com.commercehub.backend.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String status;
    private String effectiveStatus;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private OffsetDateTime placedAt;
}
