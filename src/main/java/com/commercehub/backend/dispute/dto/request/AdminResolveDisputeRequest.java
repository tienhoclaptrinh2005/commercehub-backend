package com.commercehub.backend.dispute.dto.request;

import com.commercehub.backend.dispute.entity.DisputeResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminResolveDisputeRequest(

        @NotNull(message = "Quyết định không được để trống")
        DisputeResolution decision,

        @Size(max = 5000, message = "Ghi chú phán quyết tối đa 5000 ký tự")
        String resolutionNote

) {
}
