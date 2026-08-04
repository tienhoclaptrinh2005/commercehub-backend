package com.commercehub.backend.fee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFeeConfigRequest {

    @NotNull(message = "Tỷ lệ phí không được để trống!")
    @DecimalMin(value = "0.0001", message = "Tỷ lệ phí tối thiểu là 0.01% (0.0001)")
    @DecimalMax(value = "1.0000", message = "Tỷ lệ phí tối đa là 100% (1.0000)")
    @Digits(integer = 1, fraction = 4)
    private BigDecimal feeRate;          // 0.04 = 4%, không phải 4

    @DecimalMin(value = "0", message = "Phí tối thiểu không được âm!")
    private BigDecimal minFeeAmount;     // VD: 1000 (1.000đ), null = không giới hạn

    @DecimalMin(value = "0", message = "Phí tối đa không được âm!")
    private BigDecimal maxFeeAmount;     // VD: 50000 (50.000đ), null = không giới hạn

    @Size(max = 255)
    private String description;          // Mô tả lý do thay đổi
}
