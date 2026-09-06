package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.wallet.config.SePayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SePayWebhookServiceTest {

    private static final String SECRET = "webhook-secret-test";
    private final DepositService depositService = mock(DepositService.class);
    private final SePayWebhookService service = new SePayWebhookService(
            validProperties(), depositService, new ObjectMapper());

    @Test
    void validHmacBankWebhookDelegatesAtomicDepositProcessing() throws Exception {
        byte[] body = validBody("123456789");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        service.process(timestamp, sign(timestamp, body), body);

        verify(depositService).processBankTransfer(
                "CH_12345678",
                new BigDecimal("100000"),
                "9001",
                OffsetDateTime.parse("2026-09-06T10:30:25+07:00")
        );
    }

    @Test
    void invalidHmacIsRejectedBeforePayloadIsUsed() {
        byte[] body = validBody("123456789");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> service.process(timestamp, "sha256=00", body))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED));
        verifyNoInteractions(depositService);
    }

    @Test
    void staleWebhookIsRejectedToPreventReplay() throws Exception {
        byte[] body = validBody("123456789");
        String timestamp = String.valueOf(Instant.now().minusSeconds(301).getEpochSecond());

        assertThatThrownBy(() -> service.process(timestamp, sign(timestamp, body), body))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED));
        verifyNoInteractions(depositService);
    }

    @Test
    void transferToDifferentBankAccountIsRejected() throws Exception {
        byte[] body = validBody("999999999");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> service.process(timestamp, sign(timestamp, body), body))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_NOTIFICATION));
        verifyNoInteractions(depositService);
    }

    private static byte[] validBody(String accountNumber) {
        return ("{\"id\":9001,\"gateway\":\"MBBank\","
                + "\"transactionDate\":\"2026-09-06 10:30:25\","
                + "\"accountNumber\":\"" + accountNumber + "\","
                + "\"code\":\"CH12345678\",\"content\":\"CH12345678\","
                + "\"transferType\":\"in\",\"transferAmount\":100000,"
                + "\"referenceCode\":\"FT9001\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static SePayProperties validProperties() {
        SePayProperties properties = new SePayProperties();
        properties.setWebhookSecret(SECRET);
        properties.setBankAccountNumber("123456789");
        return properties;
    }
}
