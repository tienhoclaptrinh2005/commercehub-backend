package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.wallet.config.SePayProperties;
import com.commercehub.backend.wallet.dto.request.SePayIpnRequest;
import com.commercehub.backend.wallet.dto.response.SePayCheckoutResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SePayGatewayServiceTest {

    private final DepositService depositService = mock(DepositService.class);
    private final SePayProperties properties = validProperties();
    private final SePayGatewayService service = new SePayGatewayService(properties, depositService);

    @Test
    void createsCheckoutUsingOfficialFieldOrderAndHmacSha256Signature() {
        SePayCheckoutResponse checkout = service.createCheckout(
                7L,
                new BigDecimal("100000"),
                "SEPAY_TEST_1"
        );

        assertThat(checkout.getActionUrl()).isEqualTo("https://pay-sandbox.sepay.vn/v1/checkout/init");
        assertThat(checkout.getEnvironment()).isEqualTo("sandbox");
        assertThat(checkout.getFields().keySet()).containsExactly(
                "order_amount",
                "merchant",
                "currency",
                "operation",
                "order_description",
                "order_invoice_number",
                "customer_id",
                "payment_method",
                "success_url",
                "error_url",
                "cancel_url",
                "signature"
        );
        assertThat(checkout.getFields().get("payment_method")).isEqualTo("BANK_TRANSFER");
        assertThat(checkout.getFields().get("signature"))
                .isEqualTo("jisfrwQMHOyf6YwvViGKvSusVs1KNYyiZQfZz3tums8=");
    }

    @Test
    void acceptsValidPaidIpnAndDelegatesAtomicWalletCredit() {
        SePayIpnRequest request = paidRequest("100000", "100000");

        service.processIpn("ipn-secret-test", request);

        verify(depositService).processSuccess(
                "SEPAY_TEST_1",
                new BigDecimal("100000"),
                "SEPAY_TRANSACTION_1"
        );
    }

    @Test
    void rejectsIpnWithWrongSecretBeforeReadingPaymentData() {
        assertThatThrownBy(() -> service.processIpn("wrong-secret", paidRequest("100000", "100000")))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED));

        verifyNoInteractions(depositService);
    }

    @Test
    void rejectsPaidIpnWhenOrderAndTransactionAmountsDiffer() {
        assertThatThrownBy(() -> service.processIpn("ipn-secret-test", paidRequest("100000", "99999")))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_NOTIFICATION));

        verifyNoInteractions(depositService);
    }

    @Test
    void rejectsCheckoutHostThatDoesNotMatchEnvironment() {
        properties.setCheckoutUrl("https://pay.sepay.vn/v1/checkout/init");

        assertThatThrownBy(() -> service.createCheckout(
                1L,
                new BigDecimal("100000"),
                "SEPAY_TEST_1"
        ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SYSTEM_CONFIG_ERROR));

        verifyNoInteractions(depositService);
    }

    @Test
    void voidNotificationOnlyMarksPendingDepositFailed() {
        SePayIpnRequest request = paidRequest("100000", "100000");
        request.setNotificationType("TRANSACTION_VOID");

        service.processIpn("ipn-secret-test", request);

        verify(depositService).processFailed("SEPAY_TEST_1");
    }

    private static SePayProperties validProperties() {
        SePayProperties properties = new SePayProperties();
        properties.setEnvironment("sandbox");
        properties.setMerchantId("MERCHANT_TEST");
        properties.setSecretKey("secret-test");
        properties.setIpnSecret("ipn-secret-test");
        properties.setCheckoutUrl("https://pay-sandbox.sepay.vn/v1/checkout/init");
        properties.setSuccessUrl("https://example.test/success");
        properties.setErrorUrl("https://example.test/error");
        properties.setCancelUrl("https://example.test/cancel");
        return properties;
    }

    private static SePayIpnRequest paidRequest(String orderAmount, String transactionAmount) {
        return new SePayIpnRequest(
                "ORDER_PAID",
                new SePayIpnRequest.OrderData(
                        "CAPTURED",
                        "VND",
                        new BigDecimal(orderAmount),
                        "SEPAY_TEST_1"
                ),
                new SePayIpnRequest.TransactionData(
                        "SEPAY_TRANSACTION_1",
                        "PAYMENT",
                        "APPROVED",
                        new BigDecimal(transactionAmount),
                        "VND"
                )
        );
    }
}
