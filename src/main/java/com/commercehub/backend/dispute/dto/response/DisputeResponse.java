package com.commercehub.backend.dispute.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {

    private Long id;

    private Long orderId;

    private Long orderItemId;

    private Long userId;

    private Long shopId;

    private String orderCode;

    private String shopName;

    private String productName;

    private String variantName;

    private String reason;

    private List<String> evidenceUrls;

    private String shopResponse;

    private List<String> shopEvidenceUrls;

    private String status;

    private BigDecimal refundAmount;

    private String adminNote;

    private Long resolverId;

    private String closedReason;

    private OffsetDateTime createdAt;

    private OffsetDateTime deadlineAt;

    private OffsetDateTime resolvedAt;

    private OffsetDateTime updatedAt;
}
