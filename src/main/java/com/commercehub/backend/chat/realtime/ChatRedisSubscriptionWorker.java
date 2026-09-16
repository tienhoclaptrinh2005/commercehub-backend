package com.commercehub.backend.chat.realtime;

import com.commercehub.backend.chat.config.ChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Redis SUBSCRIBE is kept on a daemon worker so Redis downtime never prevents
 * the application context from starting. PostgreSQL remains fully available;
 * the worker reconnects automatically when Redis comes back.
 */
@Slf4j
public class ChatRedisSubscriptionWorker implements SmartLifecycle {
    private final RedisConnectionFactory connectionFactory;
    private final ChatRedisSubscriber subscriber;
    private final ChatProperties properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-redis-subscriber");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private volatile RedisConnection activeConnection;

    public ChatRedisSubscriptionWorker(RedisConnectionFactory connectionFactory,
                                       ChatRedisSubscriber subscriber,
                                       ChatProperties properties) {
        this.connectionFactory = connectionFactory;
        this.subscriber = subscriber;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        executor.execute(this::subscribeLoop);
    }

    private void subscribeLoop() {
        byte[] channel = properties.getRedisChannel().getBytes(StandardCharsets.UTF_8);
        while (running) {
            try (RedisConnection connection = connectionFactory.getConnection()) {
                activeConnection = connection;
                log.info("Chat Redis subscriber connected to channel {}", properties.getRedisChannel());
                connection.subscribe(subscriber, channel);
            } catch (RuntimeException redisError) {
                if (running) log.warn("Chat Redis subscriber unavailable; retrying in 5s: {}", redisError.getMessage());
            } finally {
                activeConnection = null;
            }
            if (running) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        RedisConnection connection = activeConnection;
        if (connection != null) {
            try {
                connection.close();
            } catch (RuntimeException ignored) {
                // Shutdown remains best-effort.
            }
        }
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
