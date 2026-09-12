package com.commercehub.backend.order.entity;

/** Trạng thái tài chính của đơn đã thanh toán bằng ví. */
public enum OrderPaymentStatus {
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED
}
