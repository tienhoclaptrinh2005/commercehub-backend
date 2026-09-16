package com.commercehub.backend.chat.config;

import com.commercehub.backend.security.CustomUserDetailsService;
import com.commercehub.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Value("${commercehub.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
        registry.addEndpoint("/ws/chat").setAllowedOrigins(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    requireAuthenticated(accessor);
                    String destination = accessor.getDestination();
                    if (!"/user/queue/chat".equals(destination) && !"/user/queue/chat-errors".equals(destination)) {
                        throw new org.springframework.security.access.AccessDeniedException("Subscription không được phép");
                    }
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    requireAuthenticated(accessor);
                    if (!"/app/chat.send".equals(accessor.getDestination())) {
                        throw new org.springframework.security.access.AccessDeniedException("Destination không được phép");
                    }
                }
                return message;
            }
        });
    }

    private Authentication authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new org.springframework.security.authentication.BadCredentialsException("Thiếu access token");
        }
        String token = authorization.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Access token không hợp lệ");
        }
        var user = userDetailsService.loadUserByUsername(jwtTokenProvider.getUsernameFromJWT(token));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void requireAuthenticated(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("WebSocket chưa xác thực");
        }
    }
}
