package com.hackathon.features.bans;

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
    name = "user_bans",
    uniqueConstraints = @UniqueConstraint(columnNames = {"banner_id", "banned_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBan {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "banner_id", nullable = false)
  private UUID bannerId;

  @Column(name = "banned_id", nullable = false)
  private UUID bannedId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
