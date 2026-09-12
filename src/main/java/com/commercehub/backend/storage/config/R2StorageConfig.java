package com.commercehub.backend.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(R2StorageProperties.class)
public class R2StorageConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "true")
    S3Client r2S3Client(R2StorageProperties properties) {
        validate(properties);
        return S3Client.builder()
                .endpointOverride(endpoint(properties))
                .region(Region.of("auto"))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "true")
    S3Presigner r2S3Presigner(R2StorageProperties properties) {
        validate(properties);
        return S3Presigner.builder()
                .endpointOverride(endpoint(properties))
                .region(Region.of("auto"))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private StaticCredentialsProvider credentials(R2StorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKeyId().trim(),
                properties.getSecretAccessKey().trim()
        ));
    }

    private URI endpoint(R2StorageProperties properties) {
        URI endpoint = URI.create(properties.getEndpoint().trim());
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
            throw new IllegalStateException("R2_ENDPOINT phải là một URL HTTPS hợp lệ");
        }
        if (endpoint.getPath() != null && !endpoint.getPath().isBlank() && !"/".equals(endpoint.getPath())) {
            throw new IllegalStateException(
                    "R2_ENDPOINT chỉ được chứa endpoint tài khoản, không được kèm tên bucket"
            );
        }
        return endpoint;
    }

    private void validate(R2StorageProperties properties) {
        requireText(properties.getEndpoint(), "R2_ENDPOINT");
        requireText(properties.getAccessKeyId(), "R2_ACCESS_KEY_ID");
        requireText(properties.getSecretAccessKey(), "R2_SECRET_ACCESS_KEY");
        requireText(properties.getBucket(), "R2_BUCKET");
        requireText(properties.getPublicBaseUrl(), "MEDIA_PUBLIC_BASE_URL");

        if (!properties.getAccessKeyId().trim().matches("[A-Za-z0-9]{32}")) {
            throw new IllegalStateException(
                    "R2_ACCESS_KEY_ID phải là Access Key ID 32 ký tự chữ/số do R2 cấp, không phải API Token"
            );
        }
        if (properties.getSecretAccessKey().trim().length() < 32) {
            throw new IllegalStateException("R2_SECRET_ACCESS_KEY không đúng định dạng");
        }

        URI publicBaseUrl = URI.create(properties.getPublicBaseUrl().trim());
        if (!"https".equalsIgnoreCase(publicBaseUrl.getScheme()) || publicBaseUrl.getHost() == null) {
            throw new IllegalStateException("MEDIA_PUBLIC_BASE_URL phải là một URL HTTPS hợp lệ");
        }
        if (properties.getPresignDurationSeconds() < 60 || properties.getPresignDurationSeconds() > 900) {
            throw new IllegalStateException("R2_PRESIGN_DURATION_SECONDS phải nằm trong khoảng 60-900 giây");
        }
        if (properties.getMaxImageSizeBytes() < 1 || properties.getMaxImageSizeBytes() > 2_097_152L) {
            throw new IllegalStateException("R2_MAX_IMAGE_SIZE_BYTES không được vượt quá giới hạn cứng 2 MB");
        }
        if (properties.getProductImageWidth() != 1200 || properties.getProductImageHeight() != 900) {
            throw new IllegalStateException("Ảnh sản phẩm phải được cấu hình đúng 1200 x 900 px");
        }
        if (properties.getAvatarMaxImageSizeBytes() < 1
                || properties.getAvatarMaxImageSizeBytes() > 1_048_576L) {
            throw new IllegalStateException("R2_AVATAR_MAX_IMAGE_SIZE_BYTES không được vượt quá giới hạn cứng 1 MB");
        }
        if (properties.getAvatarImageWidth() != 512 || properties.getAvatarImageHeight() != 512) {
            throw new IllegalStateException("Ảnh đại diện phải được cấu hình đúng 512 x 512 px");
        }
        requireText(properties.getImageCacheControl(), "R2_IMAGE_CACHE_CONTROL");
        if (properties.getCreateCooldownSeconds() < 0
                || properties.getMaxPresignsPerFifteenMinutes() < 1) {
            throw new IllegalStateException("Giới hạn tạo presigned URL của R2 không hợp lệ");
        }
        if (!"memory".equalsIgnoreCase(properties.getRateLimitStore())
                && !"redis".equalsIgnoreCase(properties.getRateLimitStore())) {
            throw new IllegalStateException("R2_RATE_LIMIT_STORE chỉ chấp nhận memory hoặc redis");
        }
        requireText(properties.getRateLimitKeyPrefix(), "R2_RATE_LIMIT_KEY_PREFIX");
    }

    private void requireText(String value, String environmentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentName + " chưa được cấu hình");
        }
    }
}
