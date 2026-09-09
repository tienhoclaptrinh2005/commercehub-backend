package com.commercehub.backend.storage.dto.response;

import java.time.Instant;

public record PresignProductImageResponse(
        String objectKey,
        String uploadUrl,
        Instant expiresAt,
        long maxFileSizeBytes,
        String cacheControl
) {
}
