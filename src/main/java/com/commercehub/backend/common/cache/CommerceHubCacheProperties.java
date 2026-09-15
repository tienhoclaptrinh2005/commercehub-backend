package com.commercehub.backend.common.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "commercehub.cache")
public class CommerceHubCacheProperties {

    private String keyPrefix = "commercehub:local:cache";
    private Duration defaultTtl = Duration.ofMinutes(1);
    private Duration activeCategoriesTtl = Duration.ofMinutes(15);
    private Duration bestSellingProductsTtl = Duration.ofMinutes(1);
    private Duration publicProductPagesTtl = Duration.ofMinutes(1);
    private Duration publicShopsTtl = Duration.ofMinutes(2);
}
