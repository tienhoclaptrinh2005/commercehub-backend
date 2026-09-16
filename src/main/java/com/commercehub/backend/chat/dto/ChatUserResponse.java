package com.commercehub.backend.chat.dto;

import java.util.Set;

public record ChatUserResponse(Long id, String username, String fullName, String avatarUrl, Set<String> roles) {
}
