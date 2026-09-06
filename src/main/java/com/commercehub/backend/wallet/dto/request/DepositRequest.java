package com.commercehub.backend.wallet.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Digits(integer = 9, fraction = 0, message = "Số tiền nạp phải là số nguyên VND")
    BigDecimal amount;

    @NotBlank(message = "Idempotency key không được để trống")
    @Size(max = 100, message = "Idempotency key không được vượt quá 100 ký tự")
    String idempotencyKey;
}
