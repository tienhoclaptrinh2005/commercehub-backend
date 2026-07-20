package com.commercehub.backend.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class OrderCodeGenerator {

    private OrderCodeGenerator() {
        // Không cho tạo object
    }

    // yyyyMMddHHmmssSSS = có cả mili giây
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public static String generate(Long shopId) {

        String timestamp = LocalDateTime.now().format(FMT);

        int random = ThreadLocalRandom.current().nextInt(1000, 10000);

        return String.format(
                "ORD-S%d-%s-%04d",
                shopId,
                timestamp,
                random
        );
    }
}