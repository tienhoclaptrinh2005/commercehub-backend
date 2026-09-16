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
                connection.subscribe(subscriber, channel);
                if (!connection.isSubscribed()) {
                    throw new IllegalStateException("Redis subscription was not registered");
                }
                log.info("Chat Redis subscriber connected to channel {}", properties.getRedisChannel());
                keepSubscriptionAlive(connection);
            } catch (RuntimeException redisError) {
                if (running) log.warn("Chat Redis subscriber unavailable; retrying in 5s: {}", redisError.getMessage());
            } finally {
                activeConnection = null;
            }
            if (running && !pauseBeforeReconnect()) return;
        }
    }

    /**
     * Lettuce registers SUBSCRIBE asynchronously and returns immediately. Keep
     * the dedicated connection open until Redis reports that the subscription
     * ended; otherwise try-with-resources would reconnect every five seconds.
     */
    private void keepSubscriptionAlive(RedisConnection connection) {
        while (running && !connection.isClosed() && connection.isSubscribed()) {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean pauseBeforeReconnect() {
        try {
            Thread.sleep(5_000);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
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
