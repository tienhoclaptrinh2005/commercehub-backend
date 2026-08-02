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
public class HoldReleaseResponse {
    Long id;
    Long orderItemId;
    BigDecimal holdAmount;
    BigDecimal feeAmount;
    BigDecimal sellerNetAmount;
    String status;
    OffsetDateTime scheduledReleaseAt;
    OffsetDateTime releasedAt;
}