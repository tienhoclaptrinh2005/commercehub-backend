package com.commercehub.backend.chat.realtime;

import com.commercehub.backend.chat.dto.ChatMessageResponse;

public record ChatSocketEvent(
        String eventType,
        Long conversationId,
        ChatMessageResponse message,
        Long readerId,
        Long readThroughMessageId,
        long totalUnreadCount
) {
    public static ChatSocketEvent message(Long conversationId, ChatMessageResponse message, long unread) {
        return new ChatSocketEvent("MESSAGE_CREATED", conversationId, message, null, null, unread);
    }

    public static ChatSocketEvent read(Long conversationId, Long readerId, Long throughId, long unread) {
        return new ChatSocketEvent("MESSAGES_READ", conversationId, null, readerId, throughId, unread);
    }
}
