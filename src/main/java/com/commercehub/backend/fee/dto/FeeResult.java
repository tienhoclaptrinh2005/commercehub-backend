package com.commercehub.backend.fee.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class FeeResult {
    private Long feeConfigId;
    private BigDecimal feeRateSnapshot;
    private BigDecimal feeAmount;
    private BigDecimal sellerNetAmount;
}