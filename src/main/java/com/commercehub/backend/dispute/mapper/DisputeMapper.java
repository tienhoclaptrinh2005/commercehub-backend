package com.commercehub.backend.dispute.mapper;

import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.entity.OrderDispute;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.time.OffsetDateTime;

import com.commercehub.backend.dispute.entity.DisputeStatus;

@Component
public class DisputeMapper {

    public DisputeResponse toResponse(
            OrderDispute dispute
    ) {

        return DisputeResponse.builder()
                .id(dispute.getId())
                .orderId(dispute.getOrderId())
                .orderItemId(dispute.getOrderItemId())
                .userId(dispute.getUserId())
                .shopId(dispute.getShopId())
                .orderCode(dispute.getOrder() == null ? null : dispute.getOrder().getOrderCode())
                .shopName(dispute.getShop() == null ? null : dispute.getShop().getName())
                .productName(dispute.getOrderItem() == null ? null : dispute.getOrderItem().getProductName())
                .variantName(dispute.getOrderItem() == null ? null : dispute.getOrderItem().getVariantName())
                .disputedAmount(dispute.getOrderItem() == null ? null : dispute.getOrderItem().getLineTotal())
                .buyerUsername(dispute.getOrder() == null || dispute.getOrder().getUser() == null
                        ? null : dispute.getOrder().getUser().getUsername())
                .sellerUsername(dispute.getShop() == null || dispute.getShop().getOwner() == null
                        ? null : dispute.getShop().getOwner().getUsername())
                .reason(dispute.getReason())
                .evidenceUrls(toList(dispute.getEvidenceUrls()))
                .shopResponse(dispute.getShopResponse())
                .shopEvidenceUrls(
                        toList(dispute.getShopEvidenceUrls())
                )
                .status(dispute.getStatus())
                .resolution(dispute.getResolution())
                .resolvedBy(dispute.getResolvedBy())
                .refundAmount(dispute.getRefundAmount())
                .resolutionNote(dispute.getResolutionNote())
                .resolverId(dispute.getResolverId())
                .escalatedAt(dispute.getEscalatedAt())
                .escalatedBy(dispute.getEscalatedBy())
                .escalationReason(dispute.getEscalationReason())
                .adminOverdue(dispute.getStatus() == DisputeStatus.ADMIN_REVIEW
                        && dispute.getDeadlineAt() != null
                        && !dispute.getDeadlineAt().isAfter(OffsetDateTime.now()))
                .createdAt(dispute.getCreatedAt())
                .deadlineAt(dispute.getDeadlineAt())
                .resolvedAt(dispute.getResolvedAt())
                .updatedAt(dispute.getUpdatedAt())
                .build();
    }

    public String[] toArray(List<String> values) {

        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.toArray(new String[0]);
    }

    private List<String> toList(
            String[] values
    ) {

        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.asList(values);
    }
}
