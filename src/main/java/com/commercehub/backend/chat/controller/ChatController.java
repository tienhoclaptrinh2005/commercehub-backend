package com.commercehub.backend.chat.controller;

import com.commercehub.backend.chat.dto.*;
import com.commercehub.backend.chat.service.ChatService;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> create(@Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(HttpStatus.CREATED.value(),
                "Đã mở cuộc trò chuyện", chatService.createConversation(SecurityUtils.getCurrentUserId(), request)));
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<ConversationResponse>> list(Pageable pageable) {
        return ApiResponse.success(chatService.listConversations(SecurityUtils.getCurrentUserId(), pageable));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationResponse> get(@PathVariable Long conversationId) {
        return ApiResponse.success(chatService.getConversation(
                SecurityUtils.getCurrentUserId(), conversationId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<ChatMessagePageResponse> messages(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.success(chatService.getMessages(SecurityUtils.getCurrentUserId(), conversationId,
                beforeId, afterId, size));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ApiResponse<ChatReadResponse> markRead(@PathVariable Long conversationId,
                                                   @Valid @RequestBody MarkMessagesReadRequest request) {
        return ApiResponse.success("Đã cập nhật trạng thái đọc",
                chatService.markRead(SecurityUtils.getCurrentUserId(), conversationId, request));
    }

    @GetMapping("/unread-count")
    public ApiResponse<ChatUnreadCountResponse> unreadCount() {
        return ApiResponse.success(chatService.unreadCount(SecurityUtils.getCurrentUserId()));
    }
}
