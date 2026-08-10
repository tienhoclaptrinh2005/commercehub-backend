package com.commercehub.backend.order.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Thông tin xử lý PRE_ORDER của 1 order item.
 * deliveryContent chỉ xuất hiện trong API chi tiết đơn (buyer sở hữu / shop bán),
 * không bao giờ trả trong API danh sách. sellerNotes là ghi chú nội bộ — không expose.
 */
@Data
@Builder
public class PreOrderItemResponse {
    private String status;                 // PENDING | ACCEPTED | PROCESSING | DELIVERED | REJECTED | CANCELLED
    private String buyerInputs;            // JSON string buyer đã nhập lúc đặt
    private String deliveryContentType;    // ACCOUNT | KEY | MESSAGE | OTHER (null khi chưa giao)
    private String deliveryContent;        // null khi chưa giao
    private OffsetDateTime acceptedAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime completedAt;
}
