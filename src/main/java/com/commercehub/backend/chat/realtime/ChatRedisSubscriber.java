package com.commercehub.backend.chat.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRedisSubscriber implements MessageListener {
    private final ObjectMapper objectMapper;
    private final ChatDeliveryPublisher publisher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            publisher.consumeFromRedis(objectMapper.readValue(json, ChatDeliveryEnvelope.class));
        } catch (Exception exception) {
            log.error("Cannot consume Redis chat delivery event", exception);
        }
    }
}
