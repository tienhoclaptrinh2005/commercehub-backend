package com.commercehub.backend.chat.realtime;

import com.commercehub.backend.chat.config.ChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatDeliveryPublisherTest {
    @Test
    void redisFailureStillDeliversLocally() {
        ChatProperties properties = new ChatProperties();
        properties.setRedisPubsubEnabled(true);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.convertAndSend(anyString(), anyString())).thenThrow(new IllegalStateException("Redis down"));
        SimpMessagingTemplate socket = mock(SimpMessagingTemplate.class);
        ChatDeliveryPublisher publisher = new ChatDeliveryPublisher(properties, redis, new ObjectMapper(), socket);
        ChatSocketEvent event = ChatSocketEvent.read(3L, 2L, 9L, 0);

        publisher.deliver(new ChatDeliveryEnvelope("seller@test", event));

        verify(socket).convertAndSendToUser("seller@test", "/queue/chat", event);
    }

    @Test
    void ignoresItsOwnRedisEchoButConsumesRemoteInstanceEvent() {
        ChatProperties properties = new ChatProperties();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SimpMessagingTemplate socket = mock(SimpMessagingTemplate.class);
        ChatDeliveryPublisher publisher = new ChatDeliveryPublisher(properties, redis, new ObjectMapper(), socket);
        String ownId = (String) ReflectionTestUtils.getField(publisher, "instanceId");
        ChatSocketEvent event = ChatSocketEvent.read(3L, 2L, 9L, 0);

        publisher.consumeFromRedis(new ChatDeliveryEnvelope(ownId, "buyer@test", event));
        verifyNoInteractions(socket);

        publisher.consumeFromRedis(new ChatDeliveryEnvelope("another-instance", "buyer@test", event));
        verify(socket).convertAndSendToUser("buyer@test", "/queue/chat", event);
    }
}
