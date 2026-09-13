package com.commercehub.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AdminRequests {
    private AdminRequests() {}

    public record StatusChange(
            @NotBlank String status,
            @Size(max = 500) String reason
    ) {}

    public record ShopStatusChange(
            @NotBlank String status,
            @Size(max = 500) String reason,
            Long version
    ) {}

    public record WithdrawalDecision(
            @NotBlank String action,
            @Size(max = 500) String note
    ) {}
}
