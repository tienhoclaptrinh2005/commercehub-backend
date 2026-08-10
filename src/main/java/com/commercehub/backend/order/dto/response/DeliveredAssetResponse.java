package com.commercehub.backend.order.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 1 dòng nội dung đã giao của đơn INSTANT — đọc từ asset_delivery_logs (snapshot),
 * không đọc lại kho digital_assets.
 */
@Data
@Builder
public class DeliveredAssetResponse {
    private Long id;              // id của delivery log (hoặc asset khi fallback đơn cũ)
    private Long orderItemId;
    private String assetType;     // ACCOUNT, LICENSE, GIFTCARD, COOKIE, OTHER
    private String content;       // nguyên văn nội dung đã giao
    private OffsetDateTime deliveredAt;
}
