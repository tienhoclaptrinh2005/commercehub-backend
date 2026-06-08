package com.commercehub.backend.user.dto.request;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class UpdateProfileRequest {

    String fullName;
    String phone;
    String avatarUrl;

}
