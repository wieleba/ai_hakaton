package com.hackathon.features.users;

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
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(unique = true, nullable = false)
  private String username;

  @Column(unique = true, nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // Bumped whenever the password is (re)set. JwtAuthenticationFilter rejects any
  // token whose `iat` claim is older than this, invalidating every past JWT for
  // this user regardless of transport. Set explicitly at registration and on
  // every password-change path (change, reset); V11 migration backfills NOW()
  // for rows that predate the column. The @PrePersist default covers direct-
  // construction test fixtures that skip UserService.registerUser.
  @Column(name = "password_changed_at", nullable = false)
  private OffsetDateTime passwordChangedAt;

  // Auto-provisioned XMPP password for the Chat↔Jabber bridge. NULL when the
  // username has characters the XMPP JID spec doesn't allow (only lowercase
  // ASCII + digits + [-_.] are safe).
  @Column(name = "xmpp_password")
  private String xmppPassword;

  @PrePersist
  void ensurePasswordChangedAt() {
    if (passwordChangedAt == null) passwordChangedAt = OffsetDateTime.now();
  }
}
