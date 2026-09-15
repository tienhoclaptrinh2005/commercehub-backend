package com.commercehub.backend.common.cache;

import com.commercehub.backend.category.dto.response.CategoryResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.shop.dto.response.ShopResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CommerceHubCacheProperties.class)
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheManagerConfiguration {

    @Bean
    CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            CommerceHubCacheProperties properties,
            ObjectMapper objectMapper
    ) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> normalizedPrefix(properties.getKeyPrefix())
                        + ":" + cacheName + "::");

        Map<String, RedisCacheConfiguration> initialConfigurations =
                initialConfigurations(base, properties, objectMapper.copy());

        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(
                connectionFactory,
                BatchStrategies.scan(1_000)
        );

        return RedisCacheManager.builder(writer)
                .cacheDefaults(base.entryTtl(properties.getDefaultTtl()))
                .withInitialCacheConfigurations(initialConfigurations)
                .disableCreateOnMissingCache()
                .transactionAware()
                .enableStatistics()
                .build();
    }

    static Map<String, RedisCacheConfiguration> initialConfigurations(
            RedisCacheConfiguration base,
            CommerceHubCacheProperties properties,
            ObjectMapper objectMapper
    ) {
        JavaType categoryList = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, CategoryResponse.class);
        JavaType productList = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, ProductResponse.class);
        JavaType productPage = objectMapper.getTypeFactory()
                .constructParametricType(PageResponse.class, ProductResponse.class);
        JavaType shopPage = objectMapper.getTypeFactory()
                .constructParametricType(PageResponse.class, ShopResponse.class);

        return Map.of(
                CacheNames.ACTIVE_CATEGORIES,
                typedConfiguration(base, objectMapper, categoryList,
                        properties.getActiveCategoriesTtl()),
                CacheNames.BEST_SELLING_PRODUCTS,
                typedConfiguration(base, objectMapper, productList,
                        properties.getBestSellingProductsTtl()),
                CacheNames.PUBLIC_PRODUCT_PAGES,
                typedConfiguration(base, objectMapper, productPage,
                        properties.getPublicProductPagesTtl()),
                CacheNames.PUBLIC_SHOPS,
                typedConfiguration(base, objectMapper, shopPage,
                        properties.getPublicShopsTtl())
        );
    }

    private static RedisCacheConfiguration typedConfiguration(
            RedisCacheConfiguration base,
            ObjectMapper objectMapper,
            JavaType valueType,
            Duration ttl
    ) {
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, valueType);
        return base.entryTtl(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));
    }

    private static String normalizedPrefix(String configuredPrefix) {
        if (configuredPrefix == null || configuredPrefix.isBlank()) {
            return "commercehub:local:cache";
        }
        String prefix = configuredPrefix.trim();
        while (prefix.endsWith(":")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
