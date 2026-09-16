package com.commercehub.backend.chat.config;

import com.commercehub.backend.chat.realtime.ChatRedisSubscriber;
import com.commercehub.backend.chat.realtime.ChatRedisSubscriptionWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "commercehub.chat", name = "redis-pubsub-enabled", havingValue = "true")
public class ChatRedisPubSubConfiguration {
    private final ChatProperties properties;

    @Bean
    ChatRedisSubscriptionWorker chatRedisSubscriptionWorker(
            RedisConnectionFactory connectionFactory,
            ChatRedisSubscriber subscriber
    ) {
        return new ChatRedisSubscriptionWorker(connectionFactory, subscriber, properties);
    }
}
