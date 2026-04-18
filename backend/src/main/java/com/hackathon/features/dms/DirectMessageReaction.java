package com.hackathon.features.dms;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "direct_message_reactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageReaction {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "direct_message_id", nullable = false)
  private UUID directMessageId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 32)
  private String emoji;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
