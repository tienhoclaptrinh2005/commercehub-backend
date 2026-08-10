package com.commercehub.backend.order.dto.request;

import com.commercehub.backend.order.entity.DeliveryContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Body cho POST /api/v1/seller/orders/{id}/complete — shop giao kết quả đơn PRE_ORDER.
 * deliveryContent là nội dung giao cho khách (account/key/tin nhắn), bắt buộc.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeliverPreOrderRequest {

    @NotNull(message = "Vui lòng chọn loại nội dung giao (ACCOUNT/KEY/MESSAGE/OTHER)!")
    DeliveryContentType deliveryContentType;

    @NotBlank(message = "Nội dung giao cho người mua không được để trống!")
    @Size(max = 10000, message = "Nội dung giao tối đa 10000 ký tự!")
    String deliveryContent;

    @Size(max = 2000, message = "Ghi chú nội bộ tối đa 2000 ký tự!")
    String sellerNotes;
}
