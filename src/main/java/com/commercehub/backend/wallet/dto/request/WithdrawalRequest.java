package com.commercehub.backend.wallet.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WithdrawalRequest {

    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "500000.0", message = "Số tiền rút tối thiểu là 500,000 VND")
    @DecimalMax(value = "500000000.0", message = "Số tiền rút tối đa là 500,000,000 VND")
    BigDecimal amount;

    @NotBlank(message = "Tên ngân hàng không được để trống")
    String bankName;

    @NotBlank(message = "Số tài khoản không được để trống")
    String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    String accountName;

    /**
     * Khóa idempotency do client sinh (UUID) — retry cùng key sẽ không tạo
     * yêu cầu rút mới, không trừ ví lần 2.
     */
    @Size(max = 100, message = "Idempotency key tối đa 100 ký tự")
    String idempotencyKey;
}