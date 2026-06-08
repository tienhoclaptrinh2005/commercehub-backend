package com.commercehub.backend.user.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileResponse {
    Long id;
    String email;
    String phone;
    String username;
    String fullName;
    String avatarUrl;
    String status;
    Integer userLevel;
    BigDecimal accumulatedSpent;
    BigDecimal accumulatedEarned;
    Boolean isEmailVerified;
    Boolean isPhoneVerified;
    OffsetDateTime createdAt;
    OffsetDateTime lastActiveAt;

}
