package com.commercehub.backend.wallet.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.commercehub.backend.wallet.entity.HoldReleaseStatus;

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
    HoldReleaseStatus status;
    OffsetDateTime scheduledReleaseAt;
    OffsetDateTime releasedAt;
}
