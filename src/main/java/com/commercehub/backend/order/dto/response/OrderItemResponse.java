package com.commercehub.backend.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private String productName;
    private String variantName;
    private String productType;
    private String deliveryType;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}