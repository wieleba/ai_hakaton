package com.hackathon.features.passwordreset;

import com.hackathon.features.presence.PresenceService;
import com.hackathon.features.sessions.SessionDisconnector;
import com.hackathon.features.sessions.SessionService.EvictedEvent;
import com.hackathon.features.sessions.SessionView;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import com.hackathon.shared.security.TokenHashing;
import com.hackathon.shared.security.TokenRevocationService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
  private static final Duration PER_USER_COOLDOWN = Duration.ofMinutes(2);
  private static final long REVOCATION_TTL_SECONDS = 24 * 60 * 60L;

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetEmailBuilder emailBuilder;
  private final JavaMailSender mailSender;
  private final PresenceService presenceService;
  private final TokenRevocationService tokenRevocationService;
  private final SessionDisconnector sessionDisconnector;
  private final SimpMessagingTemplate messagingTemplate;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public void requestReset(String email) {
    Optional<User> maybe = userRepository.findByEmail(email);
    if (maybe.isEmpty()) return; // enumeration protection

    User user = maybe.get();
    OffsetDateTime cutoff = OffsetDateTime.now().minus(PER_USER_COOLDOWN);
    if (tokenRepository.existsActiveForUserSince(user.getId(), cutoff)) return;

    byte[] raw = new byte[32];
    secureRandom.nextBytes(raw);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    String hash = TokenHashing.sha256Hex(rawToken);

    PasswordResetToken row =
        PasswordResetToken.builder()
            .tokenHash(hash)
            .userId(user.getId())
            .expiresAt(OffsetDateTime.now().plus(TOKEN_LIFETIME))
            .build();
    tokenRepository.save(row);

    try {
      MimeMessage msg = emailBuilder.build(user.getEmail(), rawToken);
      mailSender.send(msg);
    } catch (MessagingException e) {
      log.error("Failed to send password reset mail to {}", user.getEmail(), e);
    }
  }

  @Transactional
  public void confirmReset(String rawToken, String newPassword) {
    if (rawToken == null || rawToken.isEmpty()) {
      throw new IllegalArgumentException("Token required");
    }
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }

    String hash = TokenHashing.sha256Hex(rawToken);
    PasswordResetToken token =
        tokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
    if (token.getUsedAt() != null) {
      throw new IllegalArgumentException("Invalid or expired token");
    }
    if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new IllegalArgumentException("Invalid or expired token");
    }

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    token.setUsedAt(OffsetDateTime.now());
    tokenRepository.save(token);

    List<SessionView> sessions = presenceService.listSessions(user.getId());
    for (SessionView s : sessions) {
      if (s.tokenHash() != null && !s.tokenHash().isEmpty()) {
        tokenRevocationService.revoke(s.tokenHash(), REVOCATION_TTL_SECONDS);
      }
      messagingTemplate.convertAndSendToUser(
          user.getId().toString(),
          "/queue/sessions",
          new EvictedEvent("EVICTED", s.sessionId()));
      sessionDisconnector.disconnect(s.sessionId());
      presenceService.markOffline(user.getId(), s.sessionId());
    }
  }
}
