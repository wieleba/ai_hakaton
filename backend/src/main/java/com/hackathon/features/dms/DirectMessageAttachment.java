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
@Table(name = "direct_message_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageAttachment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "direct_message_id", nullable = false)
  private UUID directMessageId;

  @Column(nullable = false, length = 255)
  private String filename;

  @Column(name = "mime_type", nullable = false, length = 128)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "storage_key", nullable = false, length = 255)
  private String storageKey;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
