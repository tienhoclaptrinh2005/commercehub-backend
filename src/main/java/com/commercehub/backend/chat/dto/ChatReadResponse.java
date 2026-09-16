package com.commercehub.backend.chat.dto;

import java.time.OffsetDateTime;

public record ChatReadResponse(Long conversationId, Long readerId, Long throughMessageId, OffsetDateTime readAt) {
}
