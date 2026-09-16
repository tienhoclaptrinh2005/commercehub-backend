package com.commercehub.backend.chat.entity;

import com.commercehub.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "message_reads", uniqueConstraints = @UniqueConstraint(
        name = "uq_message_read", columnNames = {"message_id", "user_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at", nullable = false, updatable = false)
    private OffsetDateTime readAt;

    @PrePersist
    void onCreate() {
        readAt = OffsetDateTime.now();
    }
}
