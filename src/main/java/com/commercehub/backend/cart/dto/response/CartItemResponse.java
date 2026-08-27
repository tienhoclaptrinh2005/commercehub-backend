package com.commercehub.backend.cart.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long productVariantId;
    private Long productId;
    private String productName;
    private String productSlug;
    private String variantName;
    private String thumbnailUrl;
    private String deliveryType;     // INSTANT | PRE_ORDER
    private String productType;
    private Long shopId;
    private String shopName;
    private BigDecimal unitPrice;    // Giá HIỆN TẠI của variant
    private Integer quantity;
    private BigDecimal lineTotal;    // unitPrice × quantity
    private Integer stockCount;      // Tồn kho hiện tại (INSTANT)
    private Boolean available;       // false nếu sản phẩm/variant/shop ngừng bán hoặc hết hàng
    private Integer maxProcessingHours; // Chỉ PRE_ORDER
    private String orderInstructions;   // Hướng dẫn seller dành cho buyer
    private String buyerInputFields;    // JSON mô tả dữ liệu seller muốn buyer cung cấp
}
