package com.commercehub.backend.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthResponse {
    String accessToken;

    // Chỉ controller dùng để ghi cookie HttpOnly; tuyệt đối không trả token này trong JSON.
    @JsonIgnore
    String refreshToken;

    @Builder.Default
    String tokenType = "Bearer";

    Long userId;
    String username;
    String email;
    String fullName;
    Set<String> roles;
    Long shopId;
    String shopStatus;
}
