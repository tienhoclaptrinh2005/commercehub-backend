package com.commercehub.backend.fee.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FeeConfigResponse {
    private Long id;
    private BigDecimal feeRate;          // 0.04 = 4%
    private BigDecimal minFeeAmount;
    private BigDecimal maxFeeAmount;
    private String description;
    private Boolean isActive;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveUntil;
    private Long createdBy;
    private OffsetDateTime createdAt;
}
