package com.commercehub.backend.chat.dto;

import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        Long nextBeforeId,
        Long nextAfterId
) {
}
