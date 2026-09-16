package com.commercehub.backend.chat.dto;

import com.commercehub.backend.chat.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendChatMessageRequest(
        @NotNull(message = "Thiếu cuộc trò chuyện") Long conversationId,
        @NotNull(message = "Thiếu mã chống gửi trùng") UUID clientMessageId,
        MessageType messageType,
        @NotBlank(message = "Tin nhắn không được để trống")
        @Size(max = 2000, message = "Tin nhắn không được vượt quá 2.000 ký tự") String content
) {
}
