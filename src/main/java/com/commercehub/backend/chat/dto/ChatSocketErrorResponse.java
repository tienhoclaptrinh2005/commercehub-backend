package com.commercehub.backend.chat.dto;

public record ChatSocketErrorResponse(String eventType, String message) {
    public static ChatSocketErrorResponse of(String message) {
        return new ChatSocketErrorResponse("CHAT_ERROR", message);
    }
}
