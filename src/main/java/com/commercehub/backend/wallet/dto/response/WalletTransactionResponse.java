package com.commercehub.backend.wallet.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletTransactionResponse {
    UUID id;
    String transactionType;
    String balanceType;
    BigDecimal amount;
    BigDecimal balanceBefore;
    BigDecimal balanceAfter;
    String description;
    OffsetDateTime createdAt;
}