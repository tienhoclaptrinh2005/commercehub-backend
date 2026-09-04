package com.commercehub.backend.user.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.OffsetDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    @JsonIgnore
    Long id;
    String username;
    String fullName;
    String avatarUrl;
    Integer userLevel;
    OffsetDateTime createdAt;
    long completedPurchaseCount;
    long successfulSaleCount;
    Set<String> roles;
    Long shopId;
    String shopName;
    String shopAvatarUrl;
}
