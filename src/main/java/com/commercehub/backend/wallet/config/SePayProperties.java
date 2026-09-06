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
    private String merchantId;
    private String secretKey;
    private String ipnSecret;
    private String checkoutUrl;
    private String successUrl;
    private String errorUrl;
    private String cancelUrl;
}
