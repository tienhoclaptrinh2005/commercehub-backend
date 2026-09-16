package com.commercehub.backend.chat.service;

import com.commercehub.backend.chat.config.ChatProperties;
import com.commercehub.backend.chat.dto.*;
import com.commercehub.backend.chat.entity.*;
import com.commercehub.backend.chat.realtime.ChatMessageCommittedEvent;
import com.commercehub.backend.chat.realtime.ChatReadCommittedEvent;
import com.commercehub.backend.chat.repository.*;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final MessageReadRepository messageReadRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ChatMessageRateLimiter rateLimiter;
    private final ChatProperties properties;
    private final MediaUrlService mediaUrlService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConversationResponse createConversation(Long currentUserId, CreateConversationRequest request) {
        Shop shop = shopRepository.findById(request.shopId())
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (!"ACTIVE".equals(shop.getStatus()) || !"ACTIVE".equals(shop.getOwner().getStatus())) {
            throw new AppException(ErrorCode.CHAT_SHOP_INACTIVE);
        }
        User buyer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        User seller = shop.getOwner();
        if (seller.getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.CHAT_CANNOT_MESSAGE_SELF);
        }

        Conversation conversation = conversationRepository
                .findByShopIdAndBuyerIdAndSellerId(shop.getId(), buyer.getId(), seller.getId())
                .orElseGet(() -> {
                    Conversation created = conversationRepository.save(Conversation.builder()
                            .shop(shop).buyer(buyer).seller(seller).status(ConversationStatus.OPEN).build());
                    participantRepository.save(ConversationParticipant.builder()
                            .conversation(created).user(buyer).participantRole(ParticipantRole.BUYER).build());
                    participantRepository.save(ConversationParticipant.builder()
                            .conversation(created).user(seller).participantRole(ParticipantRole.SELLER).build());
                    return created;
                });
        return mapConversation(conversation, currentUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listConversations(Long userId, Pageable pageable) {
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50));
        Page<ConversationResponse> page = conversationRepository.findSummariesForUser(userId, safePage)
                .map(summary -> new ConversationResponse(summary.getId(), summary.getShopId(), summary.getShopName(),
                        mediaUrlService.toPublicUrl(summary.getShopAvatarUrl()),
                        ConversationStatus.valueOf(summary.getStatus()),
                        ParticipantRole.valueOf(summary.getViewerRole()),
                        new ChatUserResponse(summary.getCounterpartId(), summary.getCounterpartUsername(),
                                summary.getCounterpartFullName(),
                                mediaUrlService.toPublicUrl(summary.getCounterpartAvatarUrl()),
                                roleSet(summary.getCounterpartRole())),
                        summary.getLastMessagePreview(), toOffsetDateTime(summary.getLastMessageAt()),
                        summary.getUnreadCount(), summary.getLastReadMessageId(),
                        toOffsetDateTime(summary.getCreatedAt())));
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(Long userId, Long conversationId) {
        return mapConversation(requireConversation(conversationId, userId), userId);
    }

    @Transactional(readOnly = true)
    public ChatMessagePageResponse getMessages(Long userId, Long conversationId, Long beforeId, Long afterId, int size) {
        requireConversation(conversationId, userId);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<ChatMessage> fetched;
        if (afterId != null) {
            fetched = messageRepository.findMissed(conversationId, afterId, PageRequest.of(0, safeSize + 1));
        } else {
            fetched = messageRepository.findHistory(conversationId, beforeId, PageRequest.of(0, safeSize + 1));
        }
        boolean hasMore = fetched.size() > safeSize;
        if (hasMore) fetched = new ArrayList<>(fetched.subList(0, safeSize));
        if (afterId == null) Collections.reverse(fetched);
        List<ChatMessageResponse> messages = fetched.stream().map(message -> mapMessage(message, userId)).toList();
        Long nextBeforeId = afterId == null && !messages.isEmpty() ? messages.get(0).id() : null;
        Long nextAfterId = afterId != null && !messages.isEmpty() ? messages.get(messages.size() - 1).id() : null;
        return new ChatMessagePageResponse(messages, hasMore, nextBeforeId, nextAfterId);
    }

    @Transactional(readOnly = true)
    public ChatUnreadCountResponse unreadCount(Long userId) {
        return new ChatUnreadCountResponse(messageRepository.countUnreadForUser(userId));
    }

    @Transactional
    public ChatReadResponse markRead(Long userId, Long conversationId, MarkMessagesReadRequest request) {
        requireConversation(conversationId, userId);
        if (!messageRepository.existsByIdAndConversationId(request.throughMessageId(), conversationId)) {
            throw new AppException(ErrorCode.CHAT_INVALID_MESSAGE);
        }
        OffsetDateTime readAt = OffsetDateTime.now();
        messageReadRepository.insertReadsThrough(conversationId, userId, request.throughMessageId());
        participantRepository.advanceReadCursor(conversationId, userId, request.throughMessageId(), readAt);
        eventPublisher.publishEvent(new ChatReadCommittedEvent(conversationId, userId, request.throughMessageId()));
        return new ChatReadResponse(conversationId, userId, request.throughMessageId(), readAt);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long senderId, SendChatMessageRequest request) {
        ChatMessage existing = messageRepository.findBySenderIdAndClientMessageId(senderId, request.clientMessageId())
                .orElse(null);
        if (existing != null) return mapMessage(existing, senderId);

        rateLimiter.check(senderId);
        Conversation conversation = requireConversation(request.conversationId(), senderId);
        if (conversation.getStatus() != ConversationStatus.OPEN) {
            throw new AppException(ErrorCode.CHAT_ACCESS_DENIED);
        }
        MessageType type = request.messageType() == null ? MessageType.TEXT : request.messageType();
        if (type != MessageType.TEXT) throw new AppException(ErrorCode.CHAT_UNSUPPORTED_MESSAGE_TYPE);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty() || content.length() > properties.getMaxMessageLength()) {
            throw new AppException(ErrorCode.CHAT_INVALID_MESSAGE);
        }
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ChatMessage saved = messageRepository.save(ChatMessage.builder()
                .conversation(conversation).sender(sender).clientMessageId(request.clientMessageId())
                .messageType(type).content(content).build());
        messageRepository.flush();
        conversation.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(conversation);
        eventPublisher.publishEvent(new ChatMessageCommittedEvent(saved.getId()));
        return mapMessage(saved, senderId);
    }

    private Conversation requireConversation(Long conversationId, Long userId) {
        if (participantRepository.findByConversationIdAndUserId(conversationId, userId).isEmpty()) {
            if (conversationRepository.existsById(conversationId)) throw new AppException(ErrorCode.CHAT_ACCESS_DENIED);
            throw new AppException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }
        return conversationRepository.findAccessible(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ACCESS_DENIED));
    }

    private ConversationResponse mapConversation(Conversation conversation, Long viewerId) {
        User counterpart = conversation.getBuyer().getId().equals(viewerId)
                ? conversation.getSeller() : conversation.getBuyer();
        var participant = participantRepository.findByConversationIdAndUserId(conversation.getId(), viewerId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ACCESS_DENIED));
        ChatMessage latest = messageRepository.findFirstByConversationIdOrderByIdDesc(conversation.getId()).orElse(null);
        String preview = latest == null ? null : latest.getContent();
        if (preview != null && preview.length() > 80) preview = preview.substring(0, 80) + "…";
        ParticipantRole viewerRole = conversation.getBuyer().getId().equals(viewerId)
                ? ParticipantRole.BUYER : ParticipantRole.SELLER;
        return new ConversationResponse(conversation.getId(), conversation.getShop().getId(), conversation.getShop().getName(),
                mediaUrlService.toPublicUrl(conversation.getSeller().getAvatarUrl()), conversation.getStatus(),
                viewerRole, mapUser(counterpart), preview, conversation.getLastMessageAt(),
                messageRepository.countUnreadInConversation(conversation.getId(), viewerId), participant.getLastReadMessageId(),
                conversation.getCreatedAt());
    }

    private ChatMessageResponse mapMessage(ChatMessage message, Long viewerId) {
        return new ChatMessageResponse(message.getId(), message.getConversation().getId(), message.getClientMessageId(),
                message.getMessageType(), message.getContent(), mapUser(message.getSender()),
                message.getSender().getId().equals(viewerId), message.getCreatedAt());
    }

    private ChatUserResponse mapUser(User user) {
        return new ChatUserResponse(user.getId(), user.getUsername(), user.getFullName(),
                mediaUrlService.toPublicUrl(user.getAvatarUrl()), user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private Set<String> roleSet(String role) {
        return role == null || role.isBlank() ? Collections.emptySet() : Set.of(role);
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
