package com.commercehub.backend.order.entity;

/**
 * Loại nội dung giao cho người mua — frontend dựa vào đây để hiển thị đúng nhãn.
 */
public enum DeliveryContentType {
    ACCOUNT,   // tài khoản: email|mật khẩu|ghi chú
    KEY,       // license key / giftcard code
    MESSAGE,   // tin nhắn xác nhận (vd: đã nâng cấp tài khoản chính chủ)
    OTHER
}
