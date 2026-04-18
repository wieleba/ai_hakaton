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
    name = "room_invitations",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"room_id", "invitee_id"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomInvitation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "inviter_id", nullable = false)
  private UUID inviterId;

  @Column(name = "invitee_id", nullable = false)
  private UUID inviteeId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
