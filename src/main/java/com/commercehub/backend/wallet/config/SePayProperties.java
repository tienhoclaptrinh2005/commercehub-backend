package com.commercehub.backend.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sepay")
public class SePayProperties {

    private String environment;
    private String webhookSecret;
    private String bankCode;
    private String bankAccountNumber;
    private String accountName;
    private String qrBaseUrl;
    private int depositTtlMinutes = 15;
    private int createCooldownSeconds = 10;
    private int maxCreatesPerFifteenMinutes = 5;
    private int maxCreatesPerDay = 20;
}
