package com.commercehub.backend.storage.dto.response;

import java.time.Instant;

public record PresignAvatarImageResponse(
        String objectKey,
        String uploadUrl,
        Instant expiresAt,
        long maxFileSizeBytes,
        int requiredWidth,
        int requiredHeight,
        String cacheControl
) {
}
