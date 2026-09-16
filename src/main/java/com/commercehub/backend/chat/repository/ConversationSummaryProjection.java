package com.commercehub.backend.chat.repository;

import java.time.Instant;

public interface ConversationSummaryProjection {
    Long getId();
    Long getShopId();
    String getShopName();
    String getShopAvatarUrl();
    String getStatus();
    Long getCounterpartId();
    String getCounterpartUsername();
    String getCounterpartFullName();
    String getCounterpartAvatarUrl();
    String getLastMessagePreview();
    Instant getLastMessageAt();
    Long getUnreadCount();
    Long getLastReadMessageId();
    Instant getCreatedAt();
}
