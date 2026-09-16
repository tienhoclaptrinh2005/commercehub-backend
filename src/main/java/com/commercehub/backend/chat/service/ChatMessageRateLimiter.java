package com.commercehub.backend.chat.service;

import com.commercehub.backend.chat.config.ChatProperties;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageRateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final ChatProperties properties;
    private final Map<Long, ArrayDeque<Long>> localWindows = new ConcurrentHashMap<>();
    private final AtomicLong redisUnavailableUntil = new AtomicLong();

    public void check(Long userId) {
        if (!properties.isRedisPubsubEnabled()) {
            checkLocal(userId);
            return;
        }
        if (System.currentTimeMillis() < redisUnavailableUntil.get()) {
            checkLocal(userId);
            return;
        }
        String key = properties.getRateLimitKeyPrefix() + ":{" + userId + "}";
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, properties.getRateLimitWindow());
            }
            if (count != null && count > properties.getMaxMessagesPerWindow()) {
                throw new AppException(ErrorCode.CHAT_RATE_LIMITED);
            }
            return;
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException redisError) {
            redisUnavailableUntil.set(System.currentTimeMillis() + 5_000);
            log.warn("Redis chat rate limit unavailable; using local fallback: {}", redisError.getMessage());
        }
        checkLocal(userId);
    }

    private void checkLocal(Long userId) {
        long now = Instant.now().toEpochMilli();
        long cutoff = now - properties.getRateLimitWindow().toMillis();
        ArrayDeque<Long> timestamps = localWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) timestamps.removeFirst();
            if (timestamps.size() >= properties.getMaxMessagesPerWindow()) {
                throw new AppException(ErrorCode.CHAT_RATE_LIMITED);
            }
            timestamps.addLast(now);
        }
    }
}
