package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.wallet.config.SePayProperties;
import com.commercehub.backend.wallet.dto.request.SePayIpnRequest;
import com.commercehub.backend.wallet.dto.response.SePayCheckoutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SePayGatewayService {

    private static final Set<String> CHECKOUT_HOSTS = Set.of(
            "pay-sandbox.sepay.vn",
            "pay.sepay.vn"
    );

    private final SePayProperties properties;
    private final DepositService depositService;

    public SePayCheckoutResponse createCheckout(Long userId, BigDecimal amount, String invoiceNumber) {
        validateCheckoutConfiguration();

        String normalizedAmount;
        try {
            normalizedAmount = amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException exception) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", normalizedAmount);
        fields.put("merchant", properties.getMerchantId());
        fields.put("currency", "VND");
        fields.put("operation", "PURCHASE");
        fields.put("order_description", "Nap tien vi CommerceHub - " + invoiceNumber);
        fields.put("order_invoice_number", invoiceNumber);
        fields.put("customer_id", String.valueOf(userId));
        fields.put("payment_method", "BANK_TRANSFER");
        fields.put("success_url", properties.getSuccessUrl());
        fields.put("error_url", properties.getErrorUrl());
        fields.put("cancel_url", properties.getCancelUrl());
        fields.put("signature", sign(fields));

        return SePayCheckoutResponse.builder()
                .actionUrl(properties.getCheckoutUrl())
                .environment(properties.getEnvironment())
                .fields(fields)
                .build();
    }

    public void processIpn(String receivedSecret, SePayIpnRequest request) {
        verifyIpnSecret(receivedSecret);

        String notificationType = request.getNotificationType();
        String invoiceNumber = request.getOrder().getOrderInvoiceNumber();

        if ("TRANSACTION_VOID".equals(notificationType)) {
            depositService.processFailed(invoiceNumber);
            return;
        }

        if (!"ORDER_PAID".equals(notificationType)
                || !"CAPTURED".equals(request.getOrder().getOrderStatus())
                || !"PAYMENT".equals(request.getTransaction().getTransactionType())
                || !"APPROVED".equals(request.getTransaction().getTransactionStatus())
                || !"VND".equals(request.getOrder().getOrderCurrency())
                || !"VND".equals(request.getTransaction().getTransactionCurrency())
                || request.getOrder().getOrderAmount()
                .compareTo(request.getTransaction().getTransactionAmount()) != 0) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_NOTIFICATION);
        }

        depositService.processSuccess(
                invoiceNumber,
                request.getOrder().getOrderAmount(),
                request.getTransaction().getTransactionId()
        );
    }

    private String sign(Map<String, String> fields) {
        String signedData = fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(
                    properties.getSecretKey().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return Base64.getEncoder().encodeToString(
                    hmac.doFinal(signedData.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    private void verifyIpnSecret(String receivedSecret) {
        requireConfigured(properties.getIpnSecret());
        if (receivedSecret == null || !MessageDigest.isEqual(
                properties.getIpnSecret().getBytes(StandardCharsets.UTF_8),
                receivedSecret.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }
    }

    private void validateCheckoutConfiguration() {
        requireConfigured(properties.getEnvironment());
        requireConfigured(properties.getMerchantId());
        requireConfigured(properties.getSecretKey());
        requireConfigured(properties.getCheckoutUrl());
        requireConfigured(properties.getSuccessUrl());
        requireConfigured(properties.getErrorUrl());
        requireConfigured(properties.getCancelUrl());

        try {
            String environment = properties.getEnvironment().trim().toLowerCase(Locale.ROOT);
            String expectedHost = switch (environment) {
                case "sandbox" -> "pay-sandbox.sepay.vn";
                case "production" -> "pay.sepay.vn";
                default -> throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
            };
            URI checkoutUri = URI.create(properties.getCheckoutUrl());
            if (!"https".equalsIgnoreCase(checkoutUri.getScheme())
                    || !CHECKOUT_HOSTS.contains(checkoutUri.getHost())
                    || !expectedHost.equals(checkoutUri.getHost())
                    || !"/v1/checkout/init".equals(checkoutUri.getPath())) {
                throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
            }
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    private void requireConfigured(String value) {
        if (value == null || value.isBlank() || value.startsWith("replace-with-")) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }
}
