package com.commercehub.backend.chat.realtime;

public record ChatReadCommittedEvent(Long conversationId, Long readerId, Long throughMessageId) {
}
