package com.hackathon.features.messages;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "message_reactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String emoji;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
