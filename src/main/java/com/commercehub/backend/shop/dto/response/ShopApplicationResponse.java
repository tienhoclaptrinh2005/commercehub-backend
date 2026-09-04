package com.commercehub.backend.shop.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class ShopApplicationResponse {
    private Long id;
    private Long ownerId;
    private String username;
    private String name;
    private String contactInfo;
    private String applicationReason;
    private String status;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
