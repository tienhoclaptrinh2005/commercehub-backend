package com.commercehub.backend.user.mapper;

import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .userLevel(user.getUserLevel() != null ? user.getUserLevel().getLevel() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}