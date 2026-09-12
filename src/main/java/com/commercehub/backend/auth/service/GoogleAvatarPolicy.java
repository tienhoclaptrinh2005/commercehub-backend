package com.commercehub.backend.auth.service;

import java.net.URI;

final class GoogleAvatarPolicy {

    private GoogleAvatarPolicy() {
    }

    static String keepStoredOrUseGoogle(String storedAvatar, String googlePictureUrl) {
        if (storedAvatar != null && !storedAvatar.isBlank()) {
            return storedAvatar;
        }
        if (googlePictureUrl == null || googlePictureUrl.isBlank()) {
            return null;
        }

        String normalized = googlePictureUrl.trim();
        if (normalized.length() > 500) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            return uri.isAbsolute()
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    ? normalized
                    : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
