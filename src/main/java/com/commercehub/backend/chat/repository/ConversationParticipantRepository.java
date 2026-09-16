package com.commercehub.backend.chat.repository;

import com.commercehub.backend.chat.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);
    List<ConversationParticipant> findAllByConversationId(Long conversationId);

    @Modifying
    @Query("UPDATE ConversationParticipant p SET p.lastReadMessageId = :messageId, p.lastReadAt = :readAt "
            + "WHERE p.conversation.id = :conversationId AND p.user.id = :userId "
            + "AND (p.lastReadMessageId IS NULL OR p.lastReadMessageId < :messageId)")
    int advanceReadCursor(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId,
                          @Param("messageId") Long messageId,
                          @Param("readAt") OffsetDateTime readAt);
}
