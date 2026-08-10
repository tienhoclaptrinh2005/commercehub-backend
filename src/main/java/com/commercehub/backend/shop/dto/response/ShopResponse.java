package com.commercehub.backend.shop.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class ShopResponse {
    private Long id;
    private Long ownerId;
    private String ownerName;
    private String name;
    private String slug;
    private String shopAvatarUrl;
    private String shopCoverUrl;
    private String description;

    private Integer totalOrders;
    private Integer totalDisputes;
    private BigDecimal disputeRate;
    private BigDecimal ratingAvg;
    private String status;

    // Chỉ được map ở API chi tiết shop; API danh sách không chạy truy vấn thống kê.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long ownerCompletedPurchaseCount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long successfulSaleCount;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
