package com.commercehub.backend.dispute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateDisputeRequest(

        @NotBlank(message = "Lý do khiếu nại không được để trống")
        @Size(max = 5000, message = "Lý do khiếu nại tối đa 5000 ký tự")
        String reason,

        @Size(max = 10, message = "Tối đa 10 bằng chứng")
        List<@Size(max = 500, message = "URL bằng chứng tối đa 500 ký tự") String> evidenceUrls

) {
}