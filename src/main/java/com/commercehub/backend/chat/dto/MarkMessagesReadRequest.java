package com.commercehub.backend.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarkMessagesReadRequest(
        @NotNull(message = "Thiếu mốc tin nhắn đã đọc")
        @Positive(message = "Mốc tin nhắn đã đọc không hợp lệ") Long throughMessageId
) {
}
