package com.commercehub.backend.auth.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class LoginResponse {
        String accessToken;
        String refreshToken;
        @Builder.Default
         String tokenType = "Bearer";
         Long id;
         String username;
         String email;
         String fullName;


}
