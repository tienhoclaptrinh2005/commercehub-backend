package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.wallet.config.SePayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class VietQrService {

    private static final String OFFICIAL_QR_HOST = "vietqr.app";
    private static final String OFFICIAL_QR_PATH = "/img";

    private final SePayProperties properties;

    public String buildQrUrl(BigDecimal amount, String paymentCode) {
        validateConfiguration();

        String normalizedAmount;
        try {
            normalizedAmount = amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException exception) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        return UriComponentsBuilder.fromUriString(properties.getQrBaseUrl())
                .queryParam("acc", properties.getBankAccountNumber())
                .queryParam("bank", properties.getBankCode())
                .queryParam("amount", normalizedAmount)
                .queryParam("des", paymentCode)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    public void validateConfiguration() {
        requireConfigured(properties.getWebhookSecret());
        requireConfigured(properties.getBankCode());
        requireConfigured(properties.getBankAccountNumber());
        requireConfigured(properties.getAccountName());
        requireConfigured(properties.getQrBaseUrl());

        try {
            URI qrUri = URI.create(properties.getQrBaseUrl());
            if (!"https".equalsIgnoreCase(qrUri.getScheme())
                    || !OFFICIAL_QR_HOST.equalsIgnoreCase(qrUri.getHost())
                    || !OFFICIAL_QR_PATH.equals(qrUri.getPath())
                    || qrUri.getQuery() != null) {
                throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
            }
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }

        if (properties.getDepositTtlMinutes() != 15
                || properties.getCreateCooldownSeconds() < 1
                || properties.getMaxCreatesPerFifteenMinutes() < 1
                || properties.getMaxCreatesPerDay() < properties.getMaxCreatesPerFifteenMinutes()) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    public String getBankCode() {
        return properties.getBankCode();
    }

    public String getBankAccountNumber() {
        return properties.getBankAccountNumber();
    }

    public String getAccountName() {
        return properties.getAccountName();
    }

    private void requireConfigured(String value) {
        if (value == null || value.isBlank() || value.startsWith("replace-with-")) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }
}
