package com.commercehub.backend.storage.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2StorageConfigTest {

    private final R2StorageConfig config = new R2StorageConfig();

    @Test
    void rejectsAnApiTokenUsedAsTheS3AccessKeyId() {
        R2StorageProperties properties = validProperties();
        properties.setAccessKeyId("not-an-r2-access-key");

        assertThatThrownBy(() -> config.r2S3Client(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access Key ID 32 ký tự");
    }

    @Test
    void rejectsAnAccessKeyContainingSignatureSeparators() {
        R2StorageProperties properties = validProperties();
        properties.setAccessKeyId("0123456789abcdef01234/6789abcdef");

        assertThatThrownBy(() -> config.r2S3Client(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 ký tự chữ/số");
    }

    @Test
    void rejectsAnImageLimitAboveTwoMegabytes() {
        R2StorageProperties properties = validProperties();
        properties.setMaxImageSizeBytes(2_097_153L);

        assertThatThrownBy(() -> config.r2S3Client(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void rejectsAnEndpointContainingTheBucketPath() {
        R2StorageProperties properties = validProperties();
        properties.setEndpoint(
                "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com/commercehub-dev"
        );

        assertThatThrownBy(() -> config.r2S3Client(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không được kèm tên bucket");
    }

    private R2StorageProperties validProperties() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setEndpoint(
                "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com"
        );
        properties.setAccessKeyId("0123456789abcdef0123456789abcdef");
        properties.setSecretAccessKey(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
        properties.setBucket("commercehub-dev");
        properties.setPublicBaseUrl("https://images-dev.example.com");
        return properties;
    }
}
