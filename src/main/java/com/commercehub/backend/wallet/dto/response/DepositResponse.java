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
public class DepositResponse {
    private Long id;
    private BigDecimal amount;
    private String provider;
    private String transactionCode;
    private String status;
    private OffsetDateTime processedAt;
    private OffsetDateTime createdAt;
}
