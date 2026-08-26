package com.commercehub.backend.dispute.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record SellerRespondRequest(

        @Size(max = 5000, message = "Phản hồi của Shop tối đa 5000 ký tự")
        String response,

        @Size(max = 10, message = "Tối đa 10 bằng chứng")
        List<@Size(max = 500, message = "URL bằng chứng tối đa 500 ký tự") String> evidenceUrls

) {
}