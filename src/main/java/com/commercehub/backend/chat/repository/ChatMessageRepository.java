package com.commercehub.backend.chat.repository;

import com.commercehub.backend.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @EntityGraph(attributePaths = {"sender", "conversation"})
    Optional<ChatMessage> findBySenderIdAndClientMessageId(Long senderId, UUID clientMessageId);

    @EntityGraph(attributePaths = {"sender"})
    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId "
            + "AND (:beforeId IS NULL OR m.id < :beforeId) ORDER BY m.id DESC")
    List<ChatMessage> findHistory(@Param("conversationId") Long conversationId,
                                  @Param("beforeId") Long beforeId,
                                  Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId "
            + "AND m.id > :afterId ORDER BY m.id ASC")
    List<ChatMessage> findMissed(@Param("conversationId") Long conversationId,
                                 @Param("afterId") Long afterId,
                                 Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    Optional<ChatMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    @Query(value = """
            SELECT COUNT(*) FROM messages m
            JOIN conversation_participants p ON p.conversation_id = m.conversation_id
            WHERE p.user_id = :userId AND m.sender_id <> :userId
              AND m.id > COALESCE(p.last_read_message_id, 0)
            """, nativeQuery = true)
    long countUnreadForUser(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*) FROM messages m
            JOIN conversation_participants p ON p.conversation_id = m.conversation_id
            WHERE p.user_id = :userId AND m.conversation_id = :conversationId
              AND m.sender_id <> :userId
              AND m.id > COALESCE(p.last_read_message_id, 0)
            """, nativeQuery = true)
    long countUnreadInConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    boolean existsByIdAndConversationId(Long id, Long conversationId);
}
