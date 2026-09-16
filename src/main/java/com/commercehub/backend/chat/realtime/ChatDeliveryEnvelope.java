package com.commercehub.backend.chat.realtime;

public record ChatDeliveryEnvelope(String sourceInstanceId, String targetPrincipal, ChatSocketEvent event) {
    public ChatDeliveryEnvelope(String targetPrincipal, ChatSocketEvent event) {
        this(null, targetPrincipal, event);
    }
}
