package com.commercehub.backend.order.entity;

public enum OrderCancellationCode {
    BUYER_REQUEST,
    SELLER_CANCELLED,
    SELLER_ACCEPTANCE_TIMEOUT,
    SELLER_PROCESSING_TIMEOUT,
    ADMIN_CANCELLED
}
