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
    private int createCooldownSeconds = 3;
    private int maxPresignsPerFifteenMinutes = 20;
}
