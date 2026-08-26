package com.commercehub.backend.order.dto.request;

import com.commercehub.backend.order.entity.DeliveryContentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Body cho POST /api/v1/seller/orders/{id}/complete — shop giao kết quả đơn PRE_ORDER.
 * deliveryContent là nội dung giao cho khách (account/key/tin nhắn), bắt buộc.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeliverPreOrderRequest {

    DeliveryContentType deliveryContentType;

    @Size(max = 10000, message = "Nội dung giao tối đa 10000 ký tự!")
    String deliveryContent;

    @Size(max = 2000, message = "Ghi chú nội bộ tối đa 2000 ký tự!")
    String sellerNotes;

    /**
     * Đơn có nhiều dòng phải giao nội dung riêng cho từng orderItem. Ba trường
     * phía trên vẫn được hỗ trợ cho đơn chỉ có một dòng để tương thích client cũ.
     */
    @Valid
    @Size(max = 50, message = "Mỗi lần hoàn tất tối đa 50 dòng sản phẩm!")
    List<DeliveryItem> items;

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class DeliveryItem {
        @NotNull(message = "Order item không được để trống!")
        Long orderItemId;

        @NotNull(message = "Vui lòng chọn loại nội dung giao!")
        DeliveryContentType deliveryContentType;

        @NotBlank(message = "Nội dung giao không được để trống!")
        @Size(max = 10000, message = "Nội dung giao tối đa 10000 ký tự!")
        String deliveryContent;

        @Size(max = 2000, message = "Ghi chú nội bộ tối đa 2000 ký tự!")
        String sellerNotes;
    }
}
