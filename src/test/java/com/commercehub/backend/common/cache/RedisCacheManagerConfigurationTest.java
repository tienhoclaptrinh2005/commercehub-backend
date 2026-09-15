package com.commercehub.backend.common.cache;

import com.commercehub.backend.product.dto.response.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheManagerConfigurationTest {

    @Test
    void configuresOnlyTheApprovedPublicCachesWithTheirOwnTtls() {
        CommerceHubCacheProperties properties = new CommerceHubCacheProperties();
        properties.setKeyPrefix("commercehub:test:cache:");
        properties.setActiveCategoriesTtl(Duration.ofMinutes(15));
        properties.setBestSellingProductsTtl(Duration.ofSeconds(45));
        properties.setPublicProductPagesTtl(Duration.ofSeconds(50));
        properties.setPublicShopsTtl(Duration.ofMinutes(2));

        Map<String, RedisCacheConfiguration> configurations =
                RedisCacheManagerConfiguration.initialConfigurations(
                        RedisCacheConfiguration.defaultCacheConfig(),
                        properties,
                        new ObjectMapper().registerModule(new JavaTimeModule())
                );

        assertThat(configurations)
                .containsOnlyKeys(
                        CacheNames.ACTIVE_CATEGORIES,
                        CacheNames.BEST_SELLING_PRODUCTS,
                        CacheNames.PUBLIC_PRODUCT_PAGES,
                        CacheNames.PUBLIC_SHOPS
                );
        assertThat(configurations.get(CacheNames.ACTIVE_CATEGORIES).getTtl())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(configurations.get(CacheNames.BEST_SELLING_PRODUCTS).getTtl())
                .isEqualTo(Duration.ofSeconds(45));
        assertThat(configurations.get(CacheNames.PUBLIC_PRODUCT_PAGES).getTtl())
                .isEqualTo(Duration.ofSeconds(50));
        assertThat(configurations.get(CacheNames.PUBLIC_SHOPS).getTtl())
                .isEqualTo(Duration.ofMinutes(2));

        ProductResponse product = ProductResponse.builder()
                .id(10L)
                .name("Cached product")
                .build();
        RedisSerializationContext.SerializationPair<Object> serialization =
                configurations.get(CacheNames.BEST_SELLING_PRODUCTS)
                        .getValueSerializationPair();
        ByteBuffer encoded = serialization.write(List.of(product));

        assertThat(serialization.read(encoded))
                .isInstanceOfSatisfying(List.class, values -> {
                    assertThat(values).hasSize(1);
                    assertThat(values.getFirst()).isInstanceOf(ProductResponse.class);
                    assertThat(((ProductResponse) values.getFirst()).getName())
                            .isEqualTo("Cached product");
                });
    }
}
