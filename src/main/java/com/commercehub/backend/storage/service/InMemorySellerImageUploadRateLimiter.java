package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "storage.r2.rate-limit-store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemorySellerImageUploadRateLimiter implements SellerImageUploadRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final R2StorageProperties properties;
    private final ConcurrentHashMap<Long, Deque<Instant>> attemptsBySeller = new ConcurrentHashMap<>();

    @Override
    public void checkAndRecord(Long sellerId) {
        Instant now = Instant.now();
        Deque<Instant> attempts = attemptsBySeller.computeIfAbsent(sellerId, ignored -> new ArrayDeque<>());

        synchronized (attempts) {
            Instant oldestAllowed = now.minus(WINDOW);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(oldestAllowed)) {
                attempts.removeFirst();
            }

            Instant lastAttempt = attempts.peekLast();
            if (lastAttempt != null && now.isBefore(lastAttempt.plusSeconds(properties.getCreateCooldownSeconds()))) {
                throw new AppException(ErrorCode.IMAGE_UPLOAD_TOO_FAST);
            }
            if (attempts.size() >= properties.getMaxPresignsPerFifteenMinutes()) {
                throw new AppException(ErrorCode.IMAGE_UPLOAD_RATE_LIMITED);
            }

            attempts.addLast(now);
        }
    }
}
