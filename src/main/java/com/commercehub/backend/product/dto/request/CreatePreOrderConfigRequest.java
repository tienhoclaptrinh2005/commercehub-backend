package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePreOrderConfigRequest {

    @NotNull(message = "ID Sản phẩm không được để trống!")
    Long productId;

    @Min(value = 24, message = "Thời gian xử lý đơn đặt hàng được cố định là 24 giờ!")
    @Max(value = 24, message = "Thời gian xử lý đơn đặt hàng được cố định là 24 giờ!")
    Integer maxProcessingHours;

    String orderInstructions;

    String buyerInputFields;
    Boolean autoRejectIfUnavailable;
}
