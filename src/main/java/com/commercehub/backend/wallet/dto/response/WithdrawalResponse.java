package com.commercehub.backend.wallet.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WithdrawalResponse {
    Long id;
    BigDecimal amount;
    BigDecimal fee;
    String bankName;
    String accountNumber;
    String accountName;
    String status;
    String adminNote;
    OffsetDateTime createdAt;
    OffsetDateTime processedAt;
}