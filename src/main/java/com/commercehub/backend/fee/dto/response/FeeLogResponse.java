package com.commercehub.backend.fee.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FeeLogResponse {
    private Long id;
    private Long feeLedgerId;
    private String fromStatus;
    private String toStatus;
    private BigDecimal feeAmount;
    private Long changedBy;          // null = System (scheduler)
    private String reason;
    private OffsetDateTime createdAt;
}
