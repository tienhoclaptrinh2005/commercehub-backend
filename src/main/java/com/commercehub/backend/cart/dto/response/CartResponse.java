package com.commercehub.backend.cart.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private Long cartId;
    private List<CartItemResponse> items;
    private Integer totalItems;      // Tổng số dòng
    private Integer totalQuantity;   // Tổng số lượng sản phẩm
    private BigDecimal totalAmount;  // Tổng tiền các item còn available
}
