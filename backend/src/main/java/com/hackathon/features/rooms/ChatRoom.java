package com.hackathon.features.rooms;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(unique = true, nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String visibility;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private OffsetDateTime updatedAt;
}
