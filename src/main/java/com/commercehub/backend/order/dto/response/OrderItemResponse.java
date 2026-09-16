package com.commercehub.backend.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private BigDecimal lineSubtotal;
    private BigDecimal voucherDiscount;

    // Chỉ có giá trị với item PRE_ORDER (service tự gắn trong chi tiết đơn)
    private PreOrderItemResponse preOrder;

    // Trạng thái khiếu nại được tính từ HoldRelease thật của từng item.
    private boolean complaintAllowed;
    private OffsetDateTime complaintDeadlineAt;
    private Long disputeId;
}
