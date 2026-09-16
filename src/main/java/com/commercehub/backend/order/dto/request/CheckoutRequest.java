package com.commercehub.backend.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutRequest {

    @NotEmpty(message = "Giỏ hàng không được để trống")
    @Size(max = 50, message = "Mỗi lần checkout tối đa 50 dòng sản phẩm")
    @Valid
    List<CheckoutItemRequest> items;

    // Tùy chọn phương thức thanh toán (hiện tại hệ thống luôn trừ ví - nạp trước mua sau)
    String paymentMethod = "WALLET";

    /**
     * Khóa idempotency do client sinh (UUID) — gửi lại cùng key sẽ trả về
     * đơn đã tạo thay vì tạo đơn mới và trừ ví lần 2.
     */
    @NotBlank(message = "Idempotency key không được để trống")
    @Size(min = 16, max = 100, message = "Idempotency key phải từ 16 đến 100 ký tự")
    String idempotencyKey;

    /** Tối đa một voucher cho mỗi nhóm shop + loại giao hàng. */
    @Valid
    @Size(max = 50, message = "Mỗi lần checkout tối đa 50 mã giảm giá")
    List<@NotNull @Valid CheckoutVoucherRequest> vouchers;

    @JsonIgnore
    Long checkoutRequestId;

    /** Voucher đã chọn cho sub-order nội bộ sau khi CheckoutService tách nhóm. */
    @JsonIgnore
    String voucherCode;
}
