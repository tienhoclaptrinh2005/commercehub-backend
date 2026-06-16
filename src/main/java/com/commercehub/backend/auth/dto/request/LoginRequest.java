package com.commercehub.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequest {

    @NotBlank(message = "Vui lòng nhập email ")
     String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
     String password;

     String deviceId;
}