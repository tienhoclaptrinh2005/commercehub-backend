package com.commercehub.backend.chat.realtime;

import com.commercehub.backend.chat.config.ChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatRedisSubscriptionWorkerTest {

    @Test
    void keepsConnectionOpenAfterAsyncSubscribeReturns() throws Exception {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        ChatRedisSubscriber subscriber = mock(ChatRedisSubscriber.class);
        ChatProperties properties = new ChatProperties();
        CountDownLatch subscribed = new CountDownLatch(1);

        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.isSubscribed()).thenReturn(true);
        when(connection.isClosed()).thenReturn(false);
        doAnswer(invocation -> {
            subscribed.countDown();
            return null;
        }).when(connection).subscribe(eq(subscriber), any(byte[][].class));

        ChatRedisSubscriptionWorker worker =
                new ChatRedisSubscriptionWorker(connectionFactory, subscriber, properties);
        try {
            worker.start();
            assertThat(subscribed.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);

            verify(connection, never()).close();
            verify(connectionFactory, times(1)).getConnection();
        } finally {
            worker.stop();
        }
    }
}
