package com.commercehub.backend.wallet.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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