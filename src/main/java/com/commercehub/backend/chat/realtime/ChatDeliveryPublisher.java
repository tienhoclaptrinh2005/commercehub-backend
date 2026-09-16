package com.commercehub.backend.chat.realtime;

import com.commercehub.backend.chat.config.ChatProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatDeliveryPublisher {
    private final ChatProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicLong redisUnavailableUntil = new AtomicLong();
    private final String instanceId = UUID.randomUUID().toString();

    public void deliver(ChatDeliveryEnvelope envelope) {
        // Người dùng nối vào chính instance này nhận ngay; Redis chỉ fan-out sang
        // các instance còn lại. sourceInstanceId ngăn subscriber tự phát trùng.
        deliverLocally(envelope);
        if (properties.isRedisPubsubEnabled() && System.currentTimeMillis() >= redisUnavailableUntil.get()) {
            try {
                ChatDeliveryEnvelope distributed = new ChatDeliveryEnvelope(instanceId,
                        envelope.targetPrincipal(), envelope.event());
                redisTemplate.convertAndSend(properties.getRedisChannel(), objectMapper.writeValueAsString(distributed));
                return;
            } catch (JsonProcessingException exception) {
                log.error("Cannot serialize committed chat event", exception);
            } catch (RuntimeException redisError) {
                redisUnavailableUntil.set(System.currentTimeMillis() + 5_000);
                log.warn("Redis Pub/Sub unavailable; delivering chat event locally: {}", redisError.getMessage());
            }
        }
    }

    public void consumeFromRedis(ChatDeliveryEnvelope envelope) {
        if (!instanceId.equals(envelope.sourceInstanceId())) deliverLocally(envelope);
    }

    public void deliverLocally(ChatDeliveryEnvelope envelope) {
        messagingTemplate.convertAndSendToUser(envelope.targetPrincipal(), "/queue/chat", envelope.event());
    }
}
