package com.commercehub.backend.wallet.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositRequest {

    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "10000.0", message = "Số tiền nạp tối thiểu là 10,000 VND")
    @DecimalMax(value = "500000000.0", message = "Số tiền nạp tối đa là 500,000,000 VND")
    BigDecimal amount;

    // Nếu sau này hỗ trợ nhiều cổng thanh toán, có thể mở comment trường này
    // @NotBlank(message = "Vui lòng chọn cổng thanh toán")
    // String provider;
}