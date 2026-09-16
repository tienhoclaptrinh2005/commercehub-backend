package com.commercehub.backend.chat.controller;

import com.commercehub.backend.chat.dto.ChatSocketErrorResponse;
import com.commercehub.backend.chat.dto.SendChatMessageRequest;
import com.commercehub.backend.chat.service.ChatService;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void send(@Valid SendChatMessageRequest request, Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        chatService.sendMessage(user.getId(), request);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/chat-errors")
    public ChatSocketErrorResponse handle(Exception exception) {
        if (exception instanceof AppException appException) {
            return ChatSocketErrorResponse.of(appException.getErrorCode().getMessage());
        }
        return ChatSocketErrorResponse.of("Không thể gửi tin nhắn. Vui lòng thử lại.");
    }
}
