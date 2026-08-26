package com.commercehub.backend.dispute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminResolveDisputeRequest(

        @NotBlank(message = "Quyết định không được để trống")
        @Pattern(regexp = "BUYER_WIN|SELLER_WIN", message = "Quyết định chỉ được là BUYER_WIN hoặc SELLER_WIN")
        String decision,

        @Size(max = 5000, message = "Ghi chú Admin tối đa 5000 ký tự")
        String adminNote

) {
}