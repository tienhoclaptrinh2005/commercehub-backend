package com.commercehub.backend.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositQrResponse {
    private Long id;
    private String transactionCode;
    private String paymentCode;
    private BigDecimal amount;
    private String status;
    private String qrUrl;
    private String bankCode;
    private String bankAccountNumber;
    private String accountName;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
}
