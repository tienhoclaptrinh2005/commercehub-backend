package com.commercehub.backend.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage.r2")
public class R2StorageProperties {

    private boolean enabled;
    private String endpoint;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucket;
    private String publicBaseUrl;
    private int presignDurationSeconds = 300;
    private long maxImageSizeBytes = 2_097_152L;
    private int productImageWidth = 1200;
    private int productImageHeight = 900;
    private long avatarMaxImageSizeBytes = 1_048_576L;
    private int avatarImageWidth = 512;
    private int avatarImageHeight = 512;
    private String imageCacheControl = "public, max-age=31536000, immutable";
    private int createCooldownSeconds = 3;
    private int maxPresignsPerFifteenMinutes = 20;
    private String rateLimitStore = "memory";
    private String rateLimitKeyPrefix = "commercehub:r2:presign";
}
