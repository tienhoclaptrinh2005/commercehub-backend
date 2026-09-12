package com.commercehub.backend.order.entity;

/** Vòng đời giao hàng của đơn; không chứa trạng thái tiền hoặc khiếu nại. */
public enum OrderStatus {
    WAITING_SELLER_ACCEPTANCE,
    PROCESSING,
    DELIVERED,
    REJECTED,
    CANCELLED
}
