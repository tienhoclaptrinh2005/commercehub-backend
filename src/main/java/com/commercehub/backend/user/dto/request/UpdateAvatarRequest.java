package com.commercehub.backend.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateAvatarRequest {
    @NotBlank(message = "Đường dẫn ảnh đại diện không được để trống!")
    @URL(message = "Đường dẫn ảnh đại diện phải là một URL hợp lệ!")
    String avatarUrl;
}