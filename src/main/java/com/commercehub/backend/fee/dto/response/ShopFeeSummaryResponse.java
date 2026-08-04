package com.commercehub.backend.fee.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ShopFeeSummaryResponse {
    private Long id;
    private Long shopId;
    private Integer periodYear;
    private Integer periodMonth;
    private BigDecimal totalSales;       // Tổng doanh thu
    private BigDecimal totalFee;         // Tổng phí đã thu
    private BigDecimal totalNet;         // Tổng seller nhận
    private BigDecimal totalRefunded;    // Tổng hoàn tiền (dispute buyer win)
    private Integer orderCount;          // Số đơn hàng
    private Integer disputeCount;        // Số khiếu nại
    private OffsetDateTime updatedAt;
}
