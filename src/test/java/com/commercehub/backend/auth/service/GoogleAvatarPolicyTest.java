package com.commercehub.backend.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAvatarPolicyTest {

    @Test
    void storesGooglePictureWhenDatabaseAvatarIsMissing() {
        assertThat(GoogleAvatarPolicy.keepStoredOrUseGoogle(
                null,
                "https://lh3.googleusercontent.com/a/example"
        )).isEqualTo("https://lh3.googleusercontent.com/a/example");
    }

    @Test
    void neverOverwritesCustomR2ObjectKeyOnLaterGoogleLogin() {
        String customObjectKey = "users/7/avatars/2026/09/custom.webp";

        assertThat(GoogleAvatarPolicy.keepStoredOrUseGoogle(
                customObjectKey,
                "https://lh3.googleusercontent.com/a/new-google-picture"
        )).isEqualTo(customObjectKey);
    }

    @Test
    void rejectsNonHttpsPictureUrls() {
        assertThat(GoogleAvatarPolicy.keepStoredOrUseGoogle(
                null,
                "http://example.com/avatar.png"
        )).isNull();
    }
}
