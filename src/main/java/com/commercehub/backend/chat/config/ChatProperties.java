package com.commercehub.backend.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "commercehub.chat")
public class ChatProperties {
    private boolean redisPubsubEnabled;
    private String redisChannel = "commercehub:local:chat:events";
    private String rateLimitKeyPrefix = "commercehub:local:chat:rate-limit";
    private int maxMessageLength = 2000;
    private int maxMessagesPerWindow = 20;
    private Duration rateLimitWindow = Duration.ofSeconds(10);
}
