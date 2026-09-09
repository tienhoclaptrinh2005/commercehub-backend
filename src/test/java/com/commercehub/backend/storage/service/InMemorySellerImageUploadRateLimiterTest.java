package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySellerImageUploadRateLimiterTest {

    @Test
    void appliesCooldownPerSeller() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setCreateCooldownSeconds(3);
        properties.setMaxPresignsPerFifteenMinutes(20);
        InMemorySellerImageUploadRateLimiter limiter =
                new InMemorySellerImageUploadRateLimiter(properties);

        limiter.checkAndRecord(7L);

        assertThatThrownBy(() -> limiter.checkAndRecord(7L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.IMAGE_UPLOAD_TOO_FAST));
    }

    @Test
    void appliesRollingWindowLimitPerSeller() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setCreateCooldownSeconds(0);
        properties.setMaxPresignsPerFifteenMinutes(2);
        InMemorySellerImageUploadRateLimiter limiter =
                new InMemorySellerImageUploadRateLimiter(properties);

        limiter.checkAndRecord(7L);
        limiter.checkAndRecord(7L);

        assertThatThrownBy(() -> limiter.checkAndRecord(7L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.IMAGE_UPLOAD_RATE_LIMITED));
    }
}
