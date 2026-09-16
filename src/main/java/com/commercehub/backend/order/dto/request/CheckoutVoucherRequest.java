package com.commercehub.backend.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckoutVoucherRequest {
    @NotNull(message = "Gian hàng của voucher không được để trống")
    private Long shopId;

    @NotBlank(message = "Loại giao hàng của voucher không được để trống")
    private String deliveryType;

    @NotBlank(message = "Mã giảm giá không được để trống")
    @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự")
    private String code;
}
