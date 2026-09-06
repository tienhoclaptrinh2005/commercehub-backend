package com.commercehub.backend.wallet.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SePayIpnRequest {

    @NotBlank
    @JsonProperty("notification_type")
    private String notificationType;

    @Valid
    @NotNull
    private OrderData order;

    @Valid
    @NotNull
    private TransactionData transaction;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderData {
        @NotBlank
        @JsonProperty("order_status")
        private String orderStatus;

        @NotBlank
        @JsonProperty("order_currency")
        private String orderCurrency;

        @NotNull
        @JsonProperty("order_amount")
        private BigDecimal orderAmount;

        @NotBlank
        @JsonProperty("order_invoice_number")
        private String orderInvoiceNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionData {
        @NotBlank
        @JsonProperty("transaction_id")
        private String transactionId;

        @NotBlank
        @JsonProperty("transaction_type")
        private String transactionType;

        @NotBlank
        @JsonProperty("transaction_status")
        private String transactionStatus;

        @NotNull
        @JsonProperty("transaction_amount")
        private BigDecimal transactionAmount;

        @NotBlank
        @JsonProperty("transaction_currency")
        private String transactionCurrency;
    }
}
