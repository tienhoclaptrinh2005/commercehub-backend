package com.commercehub.backend.chat.dto;

import jakarta.validation.constraints.NotNull;

public record CreateConversationRequest(@NotNull(message = "Thiếu gian hàng cần nhắn tin") Long shopId) {
}
