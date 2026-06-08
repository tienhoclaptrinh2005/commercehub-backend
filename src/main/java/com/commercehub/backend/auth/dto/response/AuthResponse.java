package com.commercehub.backend.auth.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthResponse {
    String accessToken;
    String refreshToken;

    @Builder.Default
    String tokenType = "Bearer";

    Long userId;
    String username;
    String email;
    String fullName;
}