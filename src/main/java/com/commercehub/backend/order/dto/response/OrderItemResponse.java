package com.commercehub.backend.order.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
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