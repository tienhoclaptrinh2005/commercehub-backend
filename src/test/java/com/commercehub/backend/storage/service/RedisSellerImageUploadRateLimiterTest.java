package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSellerImageUploadRateLimiterTest {

    @Test
    void executesOneAtomicScriptForAValidAttempt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(1L);
        RedisSellerImageUploadRateLimiter limiter =
                new RedisSellerImageUploadRateLimiter(redis, properties());

        assertThatCode(() -> limiter.checkAndRecord(7L)).doesNotThrowAnyException();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<String>> keys =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertThat(keys.getValue()).containsExactly("commercehub:r2:presign:{7}");
    }

    @Test
    void mapsRedisRateLimitResultToTheApplicationError() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(-2L);
        RedisSellerImageUploadRateLimiter limiter =
                new RedisSellerImageUploadRateLimiter(redis, properties());

        assertThatThrownBy(() -> limiter.checkAndRecord(7L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_UPLOAD_RATE_LIMITED));
    }

    private R2StorageProperties properties() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setCreateCooldownSeconds(3);
        properties.setMaxPresignsPerFifteenMinutes(20);
        properties.setRateLimitKeyPrefix("commercehub:r2:presign");
        return properties;
    }
}
