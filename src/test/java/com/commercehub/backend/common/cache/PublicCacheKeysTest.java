package com.commercehub.backend.common.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicCacheKeysTest {

    @Test
    void normalizesEquivalentProductPageRequests() {
        assertThat(PublicCacheKeys.publicProductPage(-1, 0))
                .isEqualTo(PublicCacheKeys.publicProductPage(0, 20));
        assertThat(PublicCacheKeys.publicProductPage(0, 101))
                .isEqualTo(PublicCacheKeys.publicProductPage(0, 20));
    }

    @Test
    void normalizesEquivalentBestSellingLimits() {
        assertThat(PublicCacheKeys.bestSellingProducts(0)).isEqualTo("4");
        assertThat(PublicCacheKeys.bestSellingProducts(100)).isEqualTo("12");
    }

    @Test
    void normalizesPublicShopFilters() {
        assertThat(PublicCacheKeys.publicShops(0, 8, "  VPN  ", null, "TRUSTED"))
                .isEqualTo(PublicCacheKeys.publicShops(-1, 0, "vpn", null, "trusted"));
    }
}
