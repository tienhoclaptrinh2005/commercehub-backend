package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUrlServiceTest {

    @Test
    void buildsPublicUrlAtResponseTimeSoDomainCanChangeWithoutDatabaseMigration() {
        String objectKey = "shops/3/products/2026/09/47af1e5d-0196-4f56-a8fd-d678bd760326.webp";

        R2StorageProperties oldProperties = new R2StorageProperties();
        oldProperties.setPublicBaseUrl("https://images.old-domain.test/");
        R2StorageProperties newProperties = new R2StorageProperties();
        newProperties.setPublicBaseUrl("https://cdn.new-domain.test");

        assertThat(new MediaUrlService(oldProperties).toPublicUrl(objectKey))
                .isEqualTo("https://images.old-domain.test/" + objectKey);
        assertThat(new MediaUrlService(newProperties).toPublicUrl(objectKey))
                .isEqualTo("https://cdn.new-domain.test/" + objectKey);
    }

    @Test
    void keepsLegacyAbsoluteUrlsReadable() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setPublicBaseUrl("https://images.current.test");

        assertThat(new MediaUrlService(properties).toPublicUrl("https://legacy.example/image.png"))
                .isEqualTo("https://legacy.example/image.png");
    }

    @Test
    void onlyAllowsSellerToAttachAnObjectFromTheirOwnShopPrefix() {
        MediaUrlService service = new MediaUrlService(new R2StorageProperties());

        assertThat(service.normalizeOwnedProductImageReference(
                "/shops/3/products/2026/09/image.webp",
                3L
        )).isEqualTo("shops/3/products/2026/09/image.webp");

        assertThatThrownBy(() -> service.normalizeOwnedProductImageReference(
                "shops/4/products/2026/09/image.webp",
                3L
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_UPLOAD_ACCESS_DENIED));

        assertThatThrownBy(() -> service.normalizeOwnedProductImageReference(
                "https://attacker.example/image.webp",
                3L
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_UPLOAD_REFERENCE_INVALID));
    }
}
