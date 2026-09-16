package com.commercehub.backend.chat.service;

import com.commercehub.backend.chat.config.ChatProperties;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChatMessageRateLimiterTest {
    @Test
    void redisFailureFallsBackToLocalLimitWithoutLosingChatAvailability() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenThrow(new IllegalStateException("Redis down"));
        ChatProperties properties = new ChatProperties();
        properties.setRedisPubsubEnabled(true);
        properties.setMaxMessagesPerWindow(2);
        properties.setRateLimitWindow(Duration.ofSeconds(10));
        ChatMessageRateLimiter limiter = new ChatMessageRateLimiter(redis, properties);

        assertThatCode(() -> limiter.check(7L)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(7L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHAT_RATE_LIMITED));
    }
}
