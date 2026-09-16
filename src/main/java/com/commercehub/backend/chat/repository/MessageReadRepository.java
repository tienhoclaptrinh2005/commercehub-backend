package com.commercehub.backend.chat.repository;

import com.commercehub.backend.chat.entity.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO message_reads(message_id, user_id, read_at)
            SELECT m.id, :userId, NOW()
            FROM messages m
            WHERE m.conversation_id = :conversationId
              AND m.id <= :throughMessageId
              AND m.sender_id <> :userId
            ON CONFLICT (message_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertReadsThrough(@Param("conversationId") Long conversationId,
                           @Param("userId") Long userId,
                           @Param("throughMessageId") Long throughMessageId);
}
