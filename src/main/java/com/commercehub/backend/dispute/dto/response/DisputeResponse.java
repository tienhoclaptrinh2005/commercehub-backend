package com.commercehub.backend.dispute.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import com.commercehub.backend.dispute.entity.DisputeResolution;
import com.commercehub.backend.dispute.entity.DisputeResolvedBy;
import com.commercehub.backend.dispute.entity.DisputeStatus;

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

    private DisputeStatus status;

    private DisputeResolution resolution;

    private DisputeResolvedBy resolvedBy;

    private BigDecimal refundAmount;

    private String resolutionNote;

    private Long resolverId;

    private OffsetDateTime createdAt;

    private OffsetDateTime deadlineAt;

    private OffsetDateTime resolvedAt;

    private OffsetDateTime updatedAt;
}
