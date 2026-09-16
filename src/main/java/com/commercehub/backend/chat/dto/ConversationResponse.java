package com.commercehub.backend.chat.dto;

import com.commercehub.backend.chat.entity.ConversationStatus;

import java.time.OffsetDateTime;

public record ConversationResponse(
        Long id,
        Long shopId,
        String shopName,
        String shopAvatarUrl,
        ConversationStatus status,
        ChatUserResponse counterpart,
        String lastMessagePreview,
        OffsetDateTime lastMessageAt,
        long unreadCount,
        Long lastReadMessageId,
        OffsetDateTime createdAt
) {
}
