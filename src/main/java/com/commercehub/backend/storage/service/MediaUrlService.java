package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MediaUrlService {

    private static final Pattern SAFE_OBJECT_KEY = Pattern.compile("^[A-Za-z0-9/_\\-.]{1,500}$");
    private final R2StorageProperties properties;

    /**
     * New R2-backed records store only an object key. Absolute URLs remain readable
     * for legacy records that existed before R2 was introduced.
     */
    public String toPublicUrl(String storedReference) {
        if (storedReference == null || storedReference.isBlank()) {
            return null;
        }

        String reference = storedReference.trim();
        if (isAbsoluteHttpUrl(reference)) {
            return reference;
        }
        if (!SAFE_OBJECT_KEY.matcher(reference).matches()) {
            return null;
        }
        if (properties.getPublicBaseUrl() == null || properties.getPublicBaseUrl().isBlank()) {
            return reference;
        }

        return stripTrailingSlash(properties.getPublicBaseUrl().trim()) + "/" + stripLeadingSlash(reference);
    }

    public String normalizeOwnedProductImageReference(String reference, Long shopId) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        String normalized = stripLeadingSlash(reference.trim());
        if (isAbsoluteHttpUrl(normalized) || !SAFE_OBJECT_KEY.matcher(normalized).matches()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_REFERENCE_INVALID);
        }

        String requiredPrefix = "shops/" + shopId + "/products/";
        if (!normalized.startsWith(requiredPrefix)) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_ACCESS_DENIED);
        }
        return normalized;
    }

    public boolean isOwnedProductImageReference(String reference, Long shopId) {
        try {
            return normalizeOwnedProductImageReference(reference, shopId) != null;
        } catch (AppException ignored) {
            return false;
        }
    }

    public String normalizeOwnedAvatarImageReference(String reference, Long userId) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        String normalized = stripLeadingSlash(reference.trim());
        if (isAbsoluteHttpUrl(normalized) || !SAFE_OBJECT_KEY.matcher(normalized).matches()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_REFERENCE_INVALID);
        }

        String requiredPrefix = "users/" + userId + "/avatars/";
        if (!normalized.startsWith(requiredPrefix)) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_ACCESS_DENIED);
        }
        return normalized;
    }

    public boolean isOwnedAvatarImageReference(String reference, Long userId) {
        try {
            return normalizeOwnedAvatarImageReference(reference, userId) != null;
        } catch (AppException ignored) {
            return false;
        }
    }

    private boolean isAbsoluteHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String stripLeadingSlash(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') {
            index++;
        }
        return value.substring(index);
    }

    private String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
