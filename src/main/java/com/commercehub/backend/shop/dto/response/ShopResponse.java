package com.commercehub.backend.shop.dto.response;

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

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}