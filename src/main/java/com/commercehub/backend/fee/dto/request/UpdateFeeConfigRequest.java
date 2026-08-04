package com.commercehub.backend.fee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeeConfigRequest {

    @NotNull
    @DecimalMin("0.0001") @DecimalMax("1.0000")
    private BigDecimal newFeeRate;

    @DecimalMin("0")
    private BigDecimal newMinFeeAmount;

    @DecimalMin("0")
    private BigDecimal newMaxFeeAmount;

    @NotBlank(message = "Lý do thay đổi là bắt buộc!")
    @Size(max = 255)
    private String changeReason;         // Lý do thay đổi tỷ lệ phí (audit trail)
}
