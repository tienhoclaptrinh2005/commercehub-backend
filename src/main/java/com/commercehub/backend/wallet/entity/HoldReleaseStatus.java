package com.commercehub.backend.wallet.entity;

/** Chỉ biểu diễn trạng thái tiền; chi tiết khiếu nại thuộc order_disputes. */
public enum HoldReleaseStatus {
    HOLDING,
    FROZEN,
    RELEASED,
    REFUNDED
}
