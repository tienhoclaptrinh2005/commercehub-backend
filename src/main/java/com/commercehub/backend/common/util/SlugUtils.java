package com.commercehub.backend.common.util;

import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;

public class SlugUtils {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public static String toSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "untitled-" + UUID.randomUUID().toString().substring(0, 8);
        }
        input = input.replace("Đ", "D").replace("đ", "d");

        String noWhiteSpace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhiteSpace, Normalizer.Form.NFD);

        normalized = DIACRITICS.matcher(normalized).replaceAll("");

        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase().replaceAll("-{2,}", "-").replaceAll("^-|-$", "");

        if (slug.isEmpty()) {
            return "untitled-" + UUID.randomUUID().toString().substring(0, 8);
        }

        return slug;

    }
}