package com.hackathon.features.passwordreset;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository
    extends JpaRepository<PasswordResetToken, String> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * True when the user has an unused, non-expired token created after the cutoff.
   * Powers the 2-minute per-user cooldown.
   */
  @Query(
      "SELECT COUNT(t) > 0 FROM PasswordResetToken t "
          + "WHERE t.userId = :userId "
          + "AND t.usedAt IS NULL "
          + "AND t.createdAt > :cutoff")
  boolean existsActiveForUserSince(
      @Param("userId") UUID userId, @Param("cutoff") OffsetDateTime cutoff);
}
