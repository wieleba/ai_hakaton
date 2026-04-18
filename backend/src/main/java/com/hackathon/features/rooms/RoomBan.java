package com.hackathon.features.rooms;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "room_bans",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"room_id", "banned_user_id"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBan {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "banned_user_id", nullable = false)
  private UUID bannedUserId;

  @Column(name = "banned_by_id", nullable = false)
  private UUID bannedById;

  @CreationTimestamp
  @Column(name = "banned_at", nullable = false, updatable = false)
  private OffsetDateTime bannedAt;
}
