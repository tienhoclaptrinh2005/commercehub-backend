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