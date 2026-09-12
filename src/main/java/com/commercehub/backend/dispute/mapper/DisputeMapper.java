package com.commercehub.backend.dispute.mapper;

import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.entity.OrderDispute;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
