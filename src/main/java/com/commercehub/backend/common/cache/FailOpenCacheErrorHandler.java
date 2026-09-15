package com.commercehub.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class FailOpenCacheErrorHandler implements CacheErrorHandler {

    private static final long LOG_INTERVAL_MILLIS = 30_000L;
    private final ConcurrentHashMap<String, AtomicLong> lastWarningByOperation = new ConcurrentHashMap<>();

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        warn("GET", cache, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        warn("PUT", cache, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        warn("EVICT", cache, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        warn("CLEAR", cache, exception);
    }

    private void warn(String operation, Cache cache, RuntimeException exception) {
        String warningKey = operation + ":" + cache.getName();
        AtomicLong lastWarning = lastWarningByOperation.computeIfAbsent(
                warningKey,
                ignored -> new AtomicLong(0L)
        );
        long now = System.currentTimeMillis();
        long previous = lastWarning.get();
        if (now - previous >= LOG_INTERVAL_MILLIS && lastWarning.compareAndSet(previous, now)) {
            log.warn("Redis cache {} failed for cache {}; falling back to the database",
                    operation, cache.getName());
            log.debug("Redis cache failure details", exception);
        }
    }
}
