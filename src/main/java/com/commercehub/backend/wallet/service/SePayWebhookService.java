package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.wallet.config.SePayProperties;
import com.commercehub.backend.wallet.dto.request.SePayWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class SePayWebhookService {

    private static final long MAX_TIMESTAMP_DRIFT_SECONDS = 300;
    private static final int MAX_WEBHOOK_BODY_BYTES = 32 * 1024;
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter SEPAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SePayProperties properties;
    private final DepositService depositService;
    private final ObjectMapper objectMapper;

    public void process(String timestampHeader, String signatureHeader, byte[] rawBody) {
        validateSignature(timestampHeader, signatureHeader, rawBody);
        SePayWebhookRequest request = parseRequest(rawBody);
        validatePayload(request);

        depositService.processBankTransfer(
                DepositService.toInternalTransactionCode(request.getCode()),
                request.getTransferAmount(),
                String.valueOf(request.getId()),
                parsePaidAt(request.getTransactionDate())
        );
    }

    private void validateSignature(String timestampHeader, String signatureHeader, byte[] rawBody) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank() || secret.startsWith("replace-with-")) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
        if (rawBody == null || rawBody.length == 0 || rawBody.length > MAX_WEBHOOK_BODY_BYTES
                || timestampHeader == null || signatureHeader == null) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException exception) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }

        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > MAX_TIMESTAMP_DRIFT_SECONDS) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }

        String normalizedSignature = signatureHeader.trim().toLowerCase(Locale.ROOT);
        if (!normalizedSignature.startsWith(SIGNATURE_PREFIX)) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }

        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(normalizedSignature.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }

        if (!MessageDigest.isEqual(hmacSha256(secret, timestampHeader, rawBody), actual)) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }
    }

    private byte[] hmacSha256(String secret, String timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException exception) {
            log.error("Không thể khởi tạo HMAC-SHA256 cho SePay webhook", exception);
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    private SePayWebhookRequest parseRequest(byte[] rawBody) {
        try {
            return objectMapper.readValue(rawBody, SePayWebhookRequest.class);
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_NOTIFICATION);
        }
    }

    private void validatePayload(SePayWebhookRequest request) {
        if (request.getId() == null
                || request.getId() <= 0
                || !"in".equalsIgnoreCase(request.getTransferType())
                || request.getTransferAmount() == null
                || request.getTransferAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.getAccountNumber() == null
                || !request.getAccountNumber().equals(properties.getBankAccountNumber())
                || request.getCode() == null) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_NOTIFICATION);
        }
    }

    private OffsetDateTime parsePaidAt(String transactionDate) {
        try {
            return LocalDateTime.parse(transactionDate, SEPAY_DATE_TIME)
                    .atZone(VIETNAM_ZONE)
                    .toOffsetDateTime();
        } catch (DateTimeException | NullPointerException exception) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_NOTIFICATION);
        }
    }
}
