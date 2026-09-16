package com.commercehub.backend.chat.dto;

import com.commercehub.backend.chat.entity.MessageType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        UUID clientMessageId,
        MessageType messageType,
        String content,
        ChatUserResponse sender,
        boolean ownMessage,
        OffsetDateTime createdAt
) {
}
