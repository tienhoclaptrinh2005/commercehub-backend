package com.commercehub.backend.voucher.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class VoucherPreviewRequest {
    @NotNull
    private Long shopId;

    @NotBlank
    private String deliveryType;

    @NotBlank
    private String code;

    @NotEmpty
    @Valid
    private List<@NotNull @Valid Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long productVariantId;

        @NotNull
        @Positive
        private Integer quantity;
    }
}
