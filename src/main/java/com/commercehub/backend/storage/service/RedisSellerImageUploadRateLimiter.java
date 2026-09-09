package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.r2.rate-limit-store", havingValue = "redis")
public class RedisSellerImageUploadRateLimiter implements SellerImageUploadRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final long TOO_FAST = -1L;
    private static final long RATE_LIMITED = -2L;
    private static final DefaultRedisScript<Long> CHECK_AND_RECORD_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local cooldown = tonumber(ARGV[3])
            local maximum = tonumber(ARGV[4])
            local member = ARGV[5]

            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            local latest = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
            if #latest > 0 and now - tonumber(latest[2]) < cooldown then
                return -1
            end

            local current = redis.call('ZCARD', key)
            if current >= maximum then
                return -2
            end

            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return current + 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final R2StorageProperties properties;

    @Override
    public void checkAndRecord(Long sellerId) {
        long now = Instant.now().toEpochMilli();
        long windowMillis = WINDOW.toMillis();
        long cooldownMillis = Duration.ofSeconds(properties.getCreateCooldownSeconds()).toMillis();
        String key = properties.getRateLimitKeyPrefix() + ":{" + sellerId + "}";
        String member = now + "-" + UUID.randomUUID();

        try {
            Long result = redisTemplate.execute(
                    CHECK_AND_RECORD_SCRIPT,
                    List.of(key),
                    Long.toString(now),
                    Long.toString(windowMillis),
                    Long.toString(cooldownMillis),
                    Integer.toString(properties.getMaxPresignsPerFifteenMinutes()),
                    member
            );
            if (result == null) {
                throw new AppException(ErrorCode.IMAGE_UPLOAD_RATE_LIMIT_UNAVAILABLE);
            }
            if (result == TOO_FAST) {
                throw new AppException(ErrorCode.IMAGE_UPLOAD_TOO_FAST);
            }
            if (result == RATE_LIMITED) {
                throw new AppException(ErrorCode.IMAGE_UPLOAD_RATE_LIMITED);
            }
        } catch (AppException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error("Redis upload rate limiter is unavailable", exception);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_RATE_LIMIT_UNAVAILABLE);
        }
    }
}
