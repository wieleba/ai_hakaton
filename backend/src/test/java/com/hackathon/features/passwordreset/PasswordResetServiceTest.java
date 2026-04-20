package com.hackathon.features.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hackathon.features.presence.PresenceService;
import com.hackathon.features.sessions.SessionDisconnector;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import com.hackathon.shared.security.TokenHashing;
import com.hackathon.shared.security.TokenRevocationService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class PasswordResetServiceTest {

  @Autowired private PasswordResetService service;
  @Autowired private PasswordResetTokenRepository tokenRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PresenceService presenceService;

  @MockitoSpyBean private JavaMailSender mailSender;
  @MockitoSpyBean private TokenRevocationService tokenRevocationService;
  @MockitoBean private SessionDisconnector sessionDisconnector;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;

  private User saveUser(String suffix, String rawPassword) {
    User u = new User();
    u.setUsername("pwr-" + suffix);
    u.setEmail("pwr-" + suffix + "@x.test");
    u.setPasswordHash(passwordEncoder.encode(rawPassword));
    return userRepository.save(u);
  }

  @BeforeEach
  void clear() {
    tokenRepository.deleteAll();
  }

  @Test
  void requestKnownEmailPersistsTokenAndSendsMail() {
    User u = saveUser("k1-" + System.nanoTime(), "oldpass123");

    service.requestReset(u.getEmail());

    assertThat(tokenRepository.findAll())
        .hasSize(1)
        .allSatisfy(
            t -> {
              assertThat(t.getUserId()).isEqualTo(u.getId());
              assertThat(t.getUsedAt()).isNull();
              assertThat(t.getExpiresAt()).isAfter(OffsetDateTime.now().plusMinutes(29));
            });
    verify(mailSender).send(any(MimeMessage.class));
  }

  @Test
  void requestUnknownEmailIsSilentNoOp() {
    service.requestReset("nobody-" + System.nanoTime() + "@x.test");

    assertThat(tokenRepository.findAll()).isEmpty();
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void requestWhenRecentTokenExistsSkipsSecondMail() {
    User u = saveUser("k2-" + System.nanoTime(), "oldpass123");

    service.requestReset(u.getEmail());
    service.requestReset(u.getEmail());

    assertThat(tokenRepository.findAll()).hasSize(1);
    verify(mailSender).send(any(MimeMessage.class));
  }

  @Test
  void confirmValidTokenUpdatesPasswordMarksUsedRevokesSessions() {
    User u = saveUser("k3-" + System.nanoTime(), "oldpass123");
    // Seed a live session so revoke/disconnect paths fire.
    presenceService.markOnline(u.getId(), "sess-a", "UA", "1.1.1.1", "hashA");

    service.requestReset(u.getEmail());
    ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(cap.capture());
    String rawToken = extractTokenFromSubject(cap.getValue());

    service.confirmReset(rawToken, "newpass123");

    User fresh = userRepository.findById(u.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("newpass123", fresh.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches("oldpass123", fresh.getPasswordHash())).isFalse();

    Optional<PasswordResetToken> row =
        tokenRepository.findByTokenHash(TokenHashing.sha256Hex(rawToken));
    assertThat(row).isPresent();
    assertThat(row.get().getUsedAt()).isNotNull();

    verify(tokenRevocationService).revoke(eq("hashA"), anyLong());
    verify(sessionDisconnector).disconnect("sess-a");
    verify(messagingTemplate).convertAndSendToUser(eq(u.getId().toString()), eq("/queue/sessions"), any());
    assertThat(presenceService.listSessions(u.getId())).isEmpty();
  }

  @Test
  void confirmExpiredTokenThrows() {
    User u = saveUser("k4-" + System.nanoTime(), "oldpass123");
    String raw = "expired-token-" + System.nanoTime();
    PasswordResetToken t =
        PasswordResetToken.builder()
            .tokenHash(TokenHashing.sha256Hex(raw))
            .userId(u.getId())
            .expiresAt(OffsetDateTime.now().minusMinutes(1))
            .build();
    tokenRepository.save(t);

    assertThatThrownBy(() -> service.confirmReset(raw, "newpass123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void confirmUsedTokenThrows() {
    User u = saveUser("k5-" + System.nanoTime(), "oldpass123");
    String raw = "used-token-" + System.nanoTime();
    PasswordResetToken t =
        PasswordResetToken.builder()
            .tokenHash(TokenHashing.sha256Hex(raw))
            .userId(u.getId())
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .usedAt(OffsetDateTime.now())
            .build();
    tokenRepository.save(t);

    assertThatThrownBy(() -> service.confirmReset(raw, "newpass123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void confirmUnknownTokenThrows() {
    assertThatThrownBy(() -> service.confirmReset("never-issued", "newpass123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void confirmShortPasswordThrows() {
    assertThatThrownBy(() -> service.confirmReset("anything", "short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Reads the reset link from the captured MimeMessage's text and extracts the token query param. */
  private String extractTokenFromSubject(MimeMessage msg) {
    try {
      String body = collectText(msg);
      int idx = body.indexOf("token=");
      if (idx < 0) throw new AssertionError("no token in mail body");
      String tail = body.substring(idx + "token=".length());
      int end = 0;
      while (end < tail.length()
          && !Character.isWhitespace(tail.charAt(end))
          && tail.charAt(end) != '"'
          && tail.charAt(end) != '<'
          && tail.charAt(end) != '&') end++;
      return tail.substring(0, end);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Recursively concatenates all text parts of a MimeMessage (or nested multipart). */
  private String collectText(Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String s) {
      return s;
    }
    if (content instanceof Multipart mp) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < mp.getCount(); i++) {
        sb.append(collectText(mp.getBodyPart(i))).append('\n');
      }
      return sb.toString();
    }
    return String.valueOf(content);
  }
}
