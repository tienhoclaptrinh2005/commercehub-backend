package com.commercehub.backend.common.cache;

import java.util.Locale;

public final class PublicCacheKeys {

    private PublicCacheKeys() {
    }

    public static String bestSellingProducts(int requestedLimit) {
        int normalized = requestedLimit <= 0 ? 4 : Math.min(requestedLimit, 12);
        return Integer.toString(normalized);
    }

    public static String publicProductPage(int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 || requestedSize > 100 ? 20 : requestedSize;
        return page + ":" + size;
    }

    public static String publicShops(
            int requestedPage,
            int requestedSize,
            String keyword,
            Long categoryId,
            String sort
    ) {
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 || requestedSize > 100 ? 8 : requestedSize;
        String normalizedKeyword = normalize(keyword, "", 100);
        String normalizedSort = normalize(sort, "trusted", 30);
        return page + ":" + size + ":" + normalizedKeyword + ":"
                + (categoryId == null ? "all" : categoryId) + ":" + normalizedSort;
    }

    private static String normalize(String value, String fallback, int maximumLength) {
        String normalized = value == null ? fallback : value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength);
    }
}
