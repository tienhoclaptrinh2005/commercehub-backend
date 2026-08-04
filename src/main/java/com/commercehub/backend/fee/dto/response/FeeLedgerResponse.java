package com.commercehub.backend.fee.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FeeLedgerResponse {
    private Long id;
    private Long orderItemId;
    private Long orderId;
    private Long shopId;
    private BigDecimal feeRateSnapshot;      // Tỷ lệ tại thời điểm tạo đơn
    private BigDecimal saleAmount;           // Giá bán gốc
    private BigDecimal feeAmount;            // Phí sàn thu
    private BigDecimal sellerNetAmount;      // Seller nhận về
    private BigDecimal adjustedFeeAmount;    // Phí sau khi điều chỉnh (dispute)
    private BigDecimal adjustedSellerNet;    // Net sau điều chỉnh
    private String adjustmentReason;
    private String status;                   // PENDING | COLLECTED | CANCELLED
    private OffsetDateTime feeIncurredAt;
    private OffsetDateTime collectedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime createdAt;
}
