package com.commercehub.backend.admin.dto;

import com.commercehub.backend.wallet.dto.request.WithdrawalAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            @NotNull WithdrawalAction action,
            @Size(max = 500) String note,
            @Size(max = 100) String transferReference
    ) {}
}
