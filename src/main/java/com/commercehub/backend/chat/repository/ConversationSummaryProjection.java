package com.commercehub.backend.chat.repository;

import java.time.Instant;

public interface ConversationSummaryProjection {
    Long getId();
    Long getShopId();
    String getShopName();
    String getShopAvatarUrl();
    String getStatus();
    String getViewerRole();
    Long getCounterpartId();
    String getCounterpartUsername();
    String getCounterpartFullName();
    String getCounterpartAvatarUrl();
    String getCounterpartRole();
    String getLastMessagePreview();
    Instant getLastMessageAt();
    Long getUnreadCount();
    Long getLastReadMessageId();
    Instant getCreatedAt();
}
