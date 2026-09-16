package com.commercehub.backend.chat.service;

import com.commercehub.backend.chat.config.ChatProperties;
import com.commercehub.backend.chat.dto.SendChatMessageRequest;
import com.commercehub.backend.chat.entity.*;
import com.commercehub.backend.chat.realtime.ChatMessageCommittedEvent;
import com.commercehub.backend.chat.repository.*;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private ConversationRepository conversations;
    private ConversationParticipantRepository participants;
    private ChatMessageRepository messages;
    private UserRepository users;
    private ChatMessageRateLimiter rateLimiter;
    private MediaUrlService mediaUrlService;
    private ApplicationEventPublisher events;
    private ChatService service;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        participants = mock(ConversationParticipantRepository.class);
        messages = mock(ChatMessageRepository.class);
        users = mock(UserRepository.class);
        rateLimiter = mock(ChatMessageRateLimiter.class);
        mediaUrlService = mock(MediaUrlService.class);
        events = mock(ApplicationEventPublisher.class);
        ChatProperties properties = new ChatProperties();
        service = new ChatService(conversations, participants, messages, mock(MessageReadRepository.class),
                mock(ShopRepository.class), users, rateLimiter, properties, mediaUrlService, events);
        when(mediaUrlService.toPublicUrl(nullable(String.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void outsiderCannotReadConversationHistory() {
        when(participants.findByConversationIdAndUserId(20L, 99L)).thenReturn(Optional.empty());
        when(conversations.existsById(20L)).thenReturn(true);

        assertThatThrownBy(() -> service.getMessages(99L, 20L, null, null, 30))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_ACCESS_DENIED));

        verifyNoInteractions(messages);
    }

    @Test
    void messageIsPersistedBeforeRealtimeEventIsPublished() {
        User buyer = user(1L, "buyer@test", "buyer");
        User seller = user(2L, "seller@test", "seller");
        Conversation conversation = Conversation.builder().id(20L).shop(Shop.builder().id(5L).build())
                .buyer(buyer).seller(seller).status(ConversationStatus.OPEN).build();
        ConversationParticipant membership = ConversationParticipant.builder()
                .conversation(conversation).user(buyer).participantRole(ParticipantRole.BUYER).build();
        UUID clientId = UUID.randomUUID();

        when(messages.findBySenderIdAndClientMessageId(1L, clientId)).thenReturn(Optional.empty());
        when(participants.findByConversationIdAndUserId(20L, 1L)).thenReturn(Optional.of(membership));
        when(conversations.findAccessible(20L, 1L)).thenReturn(Optional.of(conversation));
        when(users.findById(1L)).thenReturn(Optional.of(buyer));
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(100L);
            message.setCreatedAt(OffsetDateTime.now());
            return message;
        });

        var response = service.sendMessage(1L,
                new SendChatMessageRequest(20L, clientId, MessageType.TEXT, " Xin chào "));

        assertThat(response.content()).isEqualTo("Xin chào");
        assertThat(response.ownMessage()).isTrue();
        InOrder order = inOrder(messages, conversations, events);
        order.verify(messages).save(any(ChatMessage.class));
        order.verify(messages).flush();
        order.verify(conversations).save(conversation);
        order.verify(events).publishEvent(new ChatMessageCommittedEvent(100L));
    }

    @Test
    void retryWithSameClientMessageIdDoesNotCreateDuplicate() {
        User buyer = user(1L, "buyer@test", "buyer");
        Conversation conversation = Conversation.builder().id(20L).build();
        UUID clientId = UUID.randomUUID();
        ChatMessage existing = ChatMessage.builder().id(100L).conversation(conversation).sender(buyer)
                .clientMessageId(clientId).messageType(MessageType.TEXT).content("Đã lưu")
                .createdAt(OffsetDateTime.now()).build();
        when(messages.findBySenderIdAndClientMessageId(1L, clientId)).thenReturn(Optional.of(existing));

        var response = service.sendMessage(1L,
                new SendChatMessageRequest(20L, clientId, MessageType.TEXT, "Đã lưu"));

        assertThat(response.id()).isEqualTo(100L);
        verifyNoInteractions(rateLimiter, events);
        verify(messages, never()).save(any());
    }

    @Test
    void conversationSummaryConvertsNativeQueryInstantsToApiOffsetDateTimes() {
        ConversationSummaryProjection summary = mock(ConversationSummaryProjection.class);
        Instant createdAt = Instant.parse("2026-09-16T15:52:28.113Z");
        when(summary.getId()).thenReturn(20L);
        when(summary.getShopId()).thenReturn(5L);
        when(summary.getShopName()).thenReturn("CommerceHub Store");
        when(summary.getShopAvatarUrl()).thenReturn("users/2/avatars/shop.webp");
        when(summary.getStatus()).thenReturn("OPEN");
        when(summary.getViewerRole()).thenReturn("SELLER");
        when(summary.getCounterpartId()).thenReturn(2L);
        when(summary.getCounterpartUsername()).thenReturn("seller");
        when(summary.getCounterpartAvatarUrl()).thenReturn("users/1/avatars/buyer.webp");
        when(summary.getCounterpartRole()).thenReturn("BUYER");
        when(summary.getCreatedAt()).thenReturn(createdAt);
        when(summary.getUnreadCount()).thenReturn(0L);
        when(mediaUrlService.toPublicUrl("users/2/avatars/shop.webp"))
                .thenReturn("https://images.example/users/2/avatars/shop.webp");
        when(mediaUrlService.toPublicUrl("users/1/avatars/buyer.webp"))
                .thenReturn("https://images.example/users/1/avatars/buyer.webp");
        when(conversations.findSummariesForUser(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        var response = service.listConversations(1L, PageRequest.of(0, 20));

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).createdAt())
                .isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
        assertThat(response.getData().get(0).lastMessageAt()).isNull();
        assertThat(response.getData().get(0).viewerRole()).isEqualTo(ParticipantRole.SELLER);
        assertThat(response.getData().get(0).shopAvatarUrl())
                .isEqualTo("https://images.example/users/2/avatars/shop.webp");
        assertThat(response.getData().get(0).counterpart().avatarUrl())
                .isEqualTo("https://images.example/users/1/avatars/buyer.webp");
        assertThat(response.getData().get(0).counterpart().roles()).containsExactly("BUYER");
    }

    private User user(Long id, String email, String username) {
        return User.builder().id(id).email(email).username(username).fullName(username)
                .passwordHash("hash").status("ACTIVE").build();
    }
}
