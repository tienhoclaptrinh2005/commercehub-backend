package com.commercehub.backend.order.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveredAssetResponse {
    private Long id;
    private Long orderItemId;
    private String assetType; // ACCOUNT, GIFTCARD...

    private Object assetData;
}