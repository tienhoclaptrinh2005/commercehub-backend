package com.commercehub.backend.chat.dto;

import com.commercehub.backend.chat.entity.ConversationStatus;
import com.commercehub.backend.chat.entity.ParticipantRole;

import java.time.OffsetDateTime;

public record ConversationResponse(
        Long id,
        Long shopId,
        String shopName,
        String shopAvatarUrl,
        ConversationStatus status,
        ParticipantRole viewerRole,
        ChatUserResponse counterpart,
        String lastMessagePreview,
        OffsetDateTime lastMessageAt,
        long unreadCount,
        Long lastReadMessageId,
        OffsetDateTime createdAt
) {
}
