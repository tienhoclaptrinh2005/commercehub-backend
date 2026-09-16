package com.commercehub.backend.chat.repository;

import com.commercehub.backend.chat.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @EntityGraph(attributePaths = {"shop", "buyer", "seller"})
    Optional<Conversation> findByShopIdAndBuyerIdAndSellerId(Long shopId, Long buyerId, Long sellerId);

    @EntityGraph(attributePaths = {"shop", "buyer", "seller"})
    @Query("SELECT c FROM Conversation c WHERE c.id = :id AND (c.buyer.id = :userId OR c.seller.id = :userId)")
    Optional<Conversation> findAccessible(@Param("id") Long id, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"shop", "buyer", "seller"})
    @Query(value = "SELECT c FROM Conversation c WHERE c.buyer.id = :userId OR c.seller.id = :userId",
            countQuery = "SELECT COUNT(c) FROM Conversation c WHERE c.buyer.id = :userId OR c.seller.id = :userId")
    Page<Conversation> findAllForUser(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            SELECT c.id AS id,
                   c.shop_id AS shopId,
                   s.name AS shopName,
                   s.shop_avatar_url AS shopAvatarUrl,
                   c.status AS status,
                   CASE WHEN c.buyer_id = :userId THEN seller.id ELSE buyer.id END AS counterpartId,
                   CASE WHEN c.buyer_id = :userId THEN seller.username ELSE buyer.username END AS counterpartUsername,
                   CASE WHEN c.buyer_id = :userId THEN seller.full_name ELSE buyer.full_name END AS counterpartFullName,
                   CASE WHEN c.buyer_id = :userId THEN seller.avatar_url ELSE buyer.avatar_url END AS counterpartAvatarUrl,
                   CASE WHEN char_length(latest.content) > 80
                        THEN substring(latest.content FROM 1 FOR 80) || '…'
                        ELSE latest.content END AS lastMessagePreview,
                   c.last_message_at AS lastMessageAt,
                   (SELECT COUNT(*) FROM messages unread
                    WHERE unread.conversation_id = c.id
                      AND unread.sender_id <> :userId
                      AND unread.id > COALESCE(participant.last_read_message_id, 0)) AS unreadCount,
                   participant.last_read_message_id AS lastReadMessageId,
                   c.created_at AS createdAt
            FROM conversations c
            JOIN shops s ON s.id = c.shop_id
            JOIN users buyer ON buyer.id = c.buyer_id
            JOIN users seller ON seller.id = c.seller_id
            JOIN conversation_participants participant
              ON participant.conversation_id = c.id AND participant.user_id = :userId
            LEFT JOIN LATERAL (
                SELECT message.content
                FROM messages message
                WHERE message.conversation_id = c.id
                ORDER BY message.id DESC
                LIMIT 1
            ) latest ON TRUE
            WHERE c.buyer_id = :userId OR c.seller_id = :userId
            ORDER BY c.last_message_at DESC NULLS LAST, c.id DESC
            """,
            countQuery = "SELECT COUNT(*) FROM conversations c WHERE c.buyer_id = :userId OR c.seller_id = :userId",
            nativeQuery = true)
    Page<ConversationSummaryProjection> findSummariesForUser(@Param("userId") Long userId, Pageable pageable);
}
