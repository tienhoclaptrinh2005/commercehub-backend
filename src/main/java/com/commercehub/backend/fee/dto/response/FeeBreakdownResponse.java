package com.commercehub.backend.fee.dto.response;

import lombok.*;
import java.math.BigDecimal;


@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FeeBreakdownResponse {
    private Long orderItemId;
    private BigDecimal saleAmount;
    private BigDecimal feeRateSnapshot;  // VD: 0.04 hiển thị thành "4%"
    private BigDecimal feeAmount;
    private BigDecimal sellerNetAmount;
    private String status;
}
