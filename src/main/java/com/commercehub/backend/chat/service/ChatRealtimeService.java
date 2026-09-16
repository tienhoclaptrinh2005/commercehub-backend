package com.commercehub.backend.chat.service;

import com.commercehub.backend.chat.dto.ChatMessageResponse;
import com.commercehub.backend.chat.dto.ChatUserResponse;
import com.commercehub.backend.chat.entity.ChatMessage;
import com.commercehub.backend.chat.entity.Conversation;
import com.commercehub.backend.chat.realtime.*;
import com.commercehub.backend.chat.repository.ChatMessageRepository;
import com.commercehub.backend.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class ChatRealtimeService {
    private final ChatMessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatDeliveryPublisher deliveryPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onMessageCommitted(ChatMessageCommittedEvent event) {
        ChatMessage message = messageRepository.findById(event.messageId()).orElse(null);
        if (message == null) return;
        Conversation conversation = conversationRepository.findById(message.getConversation().getId()).orElse(null);
        if (conversation == null) return;
        deliverMessage(conversation, message, conversation.getBuyer().getId(), conversation.getBuyer().getEmail());
        deliverMessage(conversation, message, conversation.getSeller().getId(), conversation.getSeller().getEmail());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onReadCommitted(ChatReadCommittedEvent event) {
        Conversation conversation = conversationRepository.findById(event.conversationId()).orElse(null);
        if (conversation == null) return;
        deliverRead(conversation, event, conversation.getBuyer().getId(), conversation.getBuyer().getEmail());
        deliverRead(conversation, event, conversation.getSeller().getId(), conversation.getSeller().getEmail());
    }

    private void deliverMessage(Conversation conversation, ChatMessage message, Long targetId, String email) {
        long unread = messageRepository.countUnreadForUser(targetId);
        deliveryPublisher.deliver(new ChatDeliveryEnvelope(email,
                ChatSocketEvent.message(conversation.getId(), mapMessage(message, targetId), unread)));
    }

    private void deliverRead(Conversation conversation, ChatReadCommittedEvent event, Long targetId, String email) {
        deliveryPublisher.deliver(new ChatDeliveryEnvelope(email,
                ChatSocketEvent.read(conversation.getId(), event.readerId(), event.throughMessageId(),
                        messageRepository.countUnreadForUser(targetId))));
    }

    private ChatMessageResponse mapMessage(ChatMessage message, Long viewerId) {
        var sender = message.getSender();
        return new ChatMessageResponse(message.getId(), message.getConversation().getId(),
                message.getClientMessageId(), message.getMessageType(), message.getContent(),
                new ChatUserResponse(sender.getId(), sender.getUsername(), sender.getFullName(), sender.getAvatarUrl()),
                sender.getId().equals(viewerId), message.getCreatedAt());
    }
}
