package com.commercehub.backend.common.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheRuntimeConfiguration implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
    }
}
