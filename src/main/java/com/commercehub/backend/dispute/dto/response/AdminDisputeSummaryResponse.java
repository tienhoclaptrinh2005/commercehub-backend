package com.commercehub.backend.dispute.dto.response;

public record AdminDisputeSummaryResponse(
        long pendingCount,
        long overdueCount
) {
}
