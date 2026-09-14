package com.commercehub.backend.dispute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EscalateDisputeRequest(

        @NotBlank(message = "Lý do chuyển Admin không được để trống")
        @Size(max = 200, message = "Lý do chuyển Admin tối đa 200 ký tự")
        String reason,

        @Size(max = 10, message = "Tối đa 10 bằng chứng")
        List<@Size(max = 500, message = "URL bằng chứng tối đa 500 ký tự") String> evidenceUrls

) {
}
