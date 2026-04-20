package com.hackathon.features.passwordreset;

import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /**
   * Atomically claim the token: stamp used_at only if the row still matches
   * (unused and not expired). Returns 1 when we won the race, 0 otherwise.
   * Closes the double-spend window where two concurrent confirms could both
   * pass a read-then-check before either wrote used_at.
   */
  @Modifying
  @Transactional
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :now "
          + "WHERE t.tokenHash = :hash "
          + "AND t.usedAt IS NULL "
          + "AND t.expiresAt > :now")
  int markUsedIfActive(@Param("hash") String hash, @Param("now") OffsetDateTime now);

  /**
   * Invalidate every other unused token for the user after a successful reset —
   * otherwise a second unused token from the same user could still be redeemed
   * by whoever else has the raw value.
   */
  @Modifying
  @Transactional
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :now "
          + "WHERE t.userId = :userId "
          + "AND t.usedAt IS NULL "
          + "AND t.tokenHash <> :keepHash")
  int invalidateOtherUnusedForUser(
      @Param("userId") UUID userId,
      @Param("keepHash") String keepHash,
      @Param("now") OffsetDateTime now);
}
