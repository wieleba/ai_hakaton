package com.hackathon.features.unread;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_read_markers")
@IdClass(ChatReadMarker.Pk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReadMarker {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "chat_type", nullable = false, length = 16)
  private ChatType chatType;

  @Id
  @Column(name = "chat_id", nullable = false)
  private UUID chatId;

  @Column(name = "last_read_at", nullable = false)
  private OffsetDateTime lastReadAt;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Pk implements Serializable {
    private UUID userId;
    private ChatType chatType;
    private UUID chatId;

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Pk p)) return false;
      return Objects.equals(userId, p.userId)
          && chatType == p.chatType
          && Objects.equals(chatId, p.chatId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, chatType, chatId);
    }
  }
}
