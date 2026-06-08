package com.commercehub.backend.user.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    Long id;
    String username;
    String fullName;
    String avatarUrl;
    Integer userLevel;
    OffsetDateTime createdAt;
}