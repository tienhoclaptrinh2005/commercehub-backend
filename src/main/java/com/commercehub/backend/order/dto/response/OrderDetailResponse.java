package com.commercehub.backend.order.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class OrderDetailResponse {
    private Long id;
    private String orderCode;
    private Long shopId;
    private String shopName;
    private String deliveryType;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private BigDecimal subtotalAmount;
    private BigDecimal voucherDiscount;
    private BigDecimal totalAmount;
    private OffsetDateTime placedAt;
    private OffsetDateTime deliveredAt;

    // Danh sách sản phẩm trong đơn
    private List<OrderItemResponse> items;
}