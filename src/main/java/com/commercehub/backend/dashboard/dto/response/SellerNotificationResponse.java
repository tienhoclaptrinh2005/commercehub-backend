package com.commercehub.backend.dashboard.dto.response;

public record SellerNotificationResponse(
        long recentInstantOrderCount,
        long newPreOrderRequestCount,
        long processingPreOrderCount,
        long activeDisputeCount
) {
}
