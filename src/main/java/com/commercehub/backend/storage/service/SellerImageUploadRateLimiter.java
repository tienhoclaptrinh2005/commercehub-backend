package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SellerImageUploadRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final R2StorageProperties properties;
    private final ConcurrentHashMap<Long, Deque<Instant>> attemptsBySeller = new ConcurrentHashMap<>();

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
