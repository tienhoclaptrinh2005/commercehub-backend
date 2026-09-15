package com.commercehub.backend.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FailOpenCacheErrorHandlerTest {

    @Test
    void ignoresRedisFailuresForEveryCacheOperation() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(CacheNames.ACTIVE_CATEGORIES);
        RuntimeException failure = new RuntimeException("redis unavailable");
        FailOpenCacheErrorHandler handler = new FailOpenCacheErrorHandler();

        assertThatCode(() -> handler.handleCacheGetError(failure, cache, "all"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(failure, cache, "all", "value"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(failure, cache, "all"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(failure, cache))
                .doesNotThrowAnyException();
    }
}
