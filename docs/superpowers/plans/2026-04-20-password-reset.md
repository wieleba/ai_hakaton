# Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A logged-out user who forgot their password can recover access by entering their email, clicking a one-time link that arrives via MailHog, setting a new password, and then logging in — with all prior sessions revoked.

**Architecture:** New `com.hackathon.features.passwordreset` package with one-time token storage in Postgres (`password_reset_tokens`) and email delivery via `JavaMailSender` to a MailHog container. The confirm endpoint reuses Feature #12's `TokenRevocationService` + `SessionDisconnector` to invalidate all existing JWTs and kick open tabs.

**Tech Stack:** Spring Boot 3.5 / Java 25 / Lombok / spring-boot-starter-mail / MailHog (docker-compose) / Postgres + Flyway / React 19 + TypeScript / React Router 6.

---

## File Structure

### Backend

**New:**
- `backend/src/main/resources/db/migration/V10__password_reset_tokens.sql`
- `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetToken.java`
- `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetTokenRepository.java`
- `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetService.java`
- `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetController.java`
- `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetEmailBuilder.java`
- `backend/src/main/java/com/hackathon/shared/mail/NoopMailSender.java`
- `backend/src/test/java/com/hackathon/features/passwordreset/PasswordResetServiceTest.java`
- `backend/src/test/java/com/hackathon/features/passwordreset/PasswordResetControllerTest.java`

**Modified:**
- `backend/build.gradle` (add `spring-boot-starter-mail`)
- `backend/src/main/resources/application.yml` (mail + app props)
- `backend/src/test/resources/application-test.yml` (mail host override)
- `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java` (permit new endpoints)
- `docker-compose.yml` (MailHog service + backend env vars)

### Frontend

**New:**
- `frontend/src/services/passwordResetService.ts`
- `frontend/src/pages/ForgotPasswordPage.tsx`
- `frontend/src/pages/ResetPasswordPage.tsx`
- `frontend/src/pages/__tests__/ForgotPasswordPage.test.tsx`
- `frontend/src/pages/__tests__/ResetPasswordPage.test.tsx`

**Modified:**
- `frontend/src/App.tsx` (register `/forgot-password` and `/reset-password` public routes)
- `frontend/src/pages/LoginPage.tsx` (add "Forgot password?" link + success banner)

### Roadmap

- `FEATURES_ROADMAP.md` — mark Feature #9 complete on Task 3.

---

## Task 1: Infrastructure — MailHog, mail starter, test-profile noop

**Goal:** Add the `spring-boot-starter-mail` dependency, stand up a MailHog container in `docker-compose.yml`, configure `JavaMailSender` via `application.yml`, and add a `@Profile("test")` noop so tests never touch SMTP. No feature code yet — just infrastructure so Task 2 can inject `JavaMailSender`.

**Files:**
- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`
- Modify: `docker-compose.yml`
- Create: `backend/src/main/java/com/hackathon/shared/mail/NoopMailSender.java`

- [ ] **Step 1.1: Add the mail starter to `build.gradle`**

In `backend/build.gradle`, add one line inside the `dependencies { ... }` block, just after `spring-boot-starter-security`:

```groovy
    implementation 'org.springframework.boot:spring-boot-starter-mail'
```

- [ ] **Step 1.2: Configure mail + app properties**

Append to `backend/src/main/resources/application.yml` (top level, merging under existing `spring:` block — don't duplicate):

```yaml
spring:
  mail:
    host: ${SPRING_MAIL_HOST:localhost}
    port: ${SPRING_MAIL_PORT:1025}
    properties:
      mail.smtp.auth: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH:false}
      mail.smtp.starttls.enable: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE:false}

app:
  frontend-base-url: ${APP_FRONTEND_BASE_URL:http://localhost:5173}
  mail-from: ${APP_MAIL_FROM:noreply@chat.local}
```

Note: `application.yml` likely already has a top-level `spring:` block. Merge these keys under it rather than duplicating the header. If `spring:` doesn't exist, add it.

- [ ] **Step 1.3: Test profile — point mail at localhost (noop bean will intercept anyway)**

In `backend/src/test/resources/application-test.yml`, append:

```yaml
spring:
  mail:
    host: localhost
    port: 1025

app:
  frontend-base-url: http://localhost:5173
  mail-from: noreply@test.local
```

Same merge guidance: if `spring:` already exists at the top, nest `mail:` under it.

- [ ] **Step 1.4: Add MailHog service to `docker-compose.yml`**

Append under the top-level `services:` block (sibling of `backend`, `postgres`, etc.):

```yaml
  mailhog:
    image: mailhog/mailhog:v1.0.1
    container_name: chat-mailhog
    ports:
      - "1025:1025"
      - "8025:8025"
```

Also add to the backend service's `environment:` block:

```yaml
      SPRING_MAIL_HOST: mailhog
      SPRING_MAIL_PORT: 1025
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "false"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "false"
      APP_FRONTEND_BASE_URL: "http://localhost:5173"
      APP_MAIL_FROM: "noreply@chat.local"
```

And add `mailhog` to the backend's `depends_on:` list (if it uses one; append `- mailhog`).

- [ ] **Step 1.5: Create the `NoopMailSender` test-profile bean**

Create `backend/src/main/java/com/hackathon/shared/mail/NoopMailSender.java`:

```java
package com.hackathon.shared.mail;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

/**
 * Test-profile stub for JavaMailSender. Boots without needing a running SMTP server
 * and lets test code verify "send was called" by spying this bean. Same pattern as
 * NoopTokenRevocationService and NoopPresencePublisher.
 */
@Component
@Profile("test")
@Primary
public class NoopMailSender implements JavaMailSender {

  @Override
  public MimeMessage createMimeMessage() {
    return new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
  }

  @Override
  public MimeMessage createMimeMessage(InputStream contentStream) {
    return createMimeMessage();
  }

  @Override
  public void send(MimeMessage mimeMessage) {}

  @Override
  public void send(MimeMessage... mimeMessages) {}

  @Override
  public void send(MimeMessagePreparator mimeMessagePreparator) {}

  @Override
  public void send(MimeMessagePreparator... mimeMessagePreparators) {}

  @Override
  public void send(SimpleMailMessage simpleMessage) {}

  @Override
  public void send(SimpleMailMessage... simpleMessages) {}
}
```

- [ ] **Step 1.6: Compile + run full test suite**

Run:

```bash
cd /src/ai_hakaton/backend && ./gradlew compileJava compileTestJava test
```

Expected: `BUILD SUCCESSFUL`. No existing tests should regress — we've only added a bean that replaces the default auto-configured `JavaMailSender` in the test profile.

- [ ] **Step 1.7: Commit**

```bash
git -C /src/ai_hakaton add backend/build.gradle \
    backend/src/main/resources/application.yml \
    backend/src/test/resources/application-test.yml \
    docker-compose.yml \
    backend/src/main/java/com/hackathon/shared/mail/NoopMailSender.java
git -C /src/ai_hakaton commit -m "chore(password-reset): wire mail starter + MailHog infra" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

Plain `-m` flags. No `--no-verify`. Do NOT touch `.claude/settings.local.json`.

---

## Task 2: Backend feature — migration, entity, service, controller, tests

**Goal:** Full backend implementation of the two endpoints plus the token persistence and email builder. Ships as one green commit (entity + service + controller + tests together) per the repo's green-commit rule.

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__password_reset_tokens.sql`
- Create: `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetToken.java`
- Create: `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetTokenRepository.java`
- Create: `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetEmailBuilder.java`
- Create: `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetService.java`
- Create: `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetController.java`
- Create: `backend/src/test/java/com/hackathon/features/passwordreset/PasswordResetServiceTest.java`
- Create: `backend/src/test/java/com/hackathon/features/passwordreset/PasswordResetControllerTest.java`
- Modify: `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java`

- [ ] **Step 2.1: Create the Flyway migration**

Create `backend/src/main/resources/db/migration/V10__password_reset_tokens.sql`:

```sql
CREATE TABLE password_reset_tokens (
    token_hash  CHAR(64)    PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_pwreset_user_active
    ON password_reset_tokens (user_id)
    WHERE used_at IS NULL;
```

- [ ] **Step 2.2: Create the JPA entity**

Create `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetToken.java`:

```java
package com.hackathon.features.passwordreset;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "password_reset_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

  @Id
  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;
}
```

- [ ] **Step 2.3: Create the repository**

Create `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetTokenRepository.java`:

```java
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
```

- [ ] **Step 2.4: Create the email builder**

Create `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetEmailBuilder.java`:

```java
package com.hackathon.features.passwordreset;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetEmailBuilder {

  private final JavaMailSender mailSender;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Value("${app.mail-from:noreply@chat.local}")
  private String mailFrom;

  public MimeMessage build(String toEmail, String rawToken) throws MessagingException {
    String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
    String plain =
        "You (or someone else) requested a password reset for your account.\n\n"
            + "To set a new password, open this link within 30 minutes:\n"
            + "  "
            + link
            + "\n\n"
            + "If you didn't request this, you can ignore this email"
            + " — your password stays unchanged.\n";
    String html =
        "<p>You (or someone else) requested a password reset for your account.</p>"
            + "<p>To set a new password, open this link within 30 minutes:</p>"
            + "<p><a href=\""
            + link
            + "\">"
            + link
            + "</a></p>"
            + "<p>If you didn't request this, you can ignore this email"
            + " — your password stays unchanged.</p>";

    MimeMessage msg = mailSender.createMimeMessage();
    MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
    h.setFrom(mailFrom);
    h.setTo(toEmail);
    h.setSubject("Reset your password");
    h.setText(plain, html);
    return msg;
  }
}
```

- [ ] **Step 2.5: Create the service**

Create `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetService.java`:

```java
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
```

- [ ] **Step 2.6: Create the controller**

Create `backend/src/main/java/com/hackathon/features/passwordreset/PasswordResetController.java`:

```java
package com.hackathon.features.passwordreset;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  record RequestBodyDto(String email) {}

  record ConfirmBodyDto(String token, String newPassword) {}

  @PostMapping("/request")
  public ResponseEntity<Void> request(@RequestBody RequestBodyDto body) {
    if (body == null || body.email() == null || body.email().isBlank()) {
      // Still 204 — enumeration protection applies to bad input too.
      return ResponseEntity.noContent().build();
    }
    passwordResetService.requestReset(body.email());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(@RequestBody ConfirmBodyDto body) {
    try {
      passwordResetService.confirmReset(body.token(), body.newPassword());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 2.7: Permit the endpoints in `SecurityConfig`**

In `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java`, change the `authorizeHttpRequests` block to add `/api/password-reset/**` to the permit list. Replace:

```java
            auth.requestMatchers("/api/users/register", "/api/users/login")
                    .permitAll()
                    .requestMatchers("/ws/chat/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
```

with:

```java
            auth.requestMatchers("/api/users/register", "/api/users/login")
                    .permitAll()
                    .requestMatchers("/api/password-reset/**")
                    .permitAll()
                    .requestMatchers("/ws/chat/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
```

- [ ] **Step 2.8: Write the service test**

Create `backend/src/test/java/com/hackathon/features/passwordreset/PasswordResetServiceTest.java`:

```java
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
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
      Object content = msg.getContent();
      String body;
      if (content instanceof String s) {
        body = s;
      } else {
        body = content.toString();
      }
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
}
```

- [ ] **Step 2.9: Write the controller test**

Create `backend/src/test/java/com/hackathon/features/passwordreset/PasswordResetControllerTest.java`:

```java
package com.hackathon.features.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.features.sessions.SessionDisconnector;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import com.hackathon.shared.security.TokenHashing;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordResetTokenRepository tokenRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private SessionDisconnector sessionDisconnector;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;

  @Test
  void request_returns204_forAnyEmail() throws Exception {
    mvc.perform(post("/api/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@x.test\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void request_returns204_forBlankEmail() throws Exception {
    mvc.perform(post("/api/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void confirm_unknownToken_is400() throws Exception {
    mvc.perform(post("/api/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"nope\",\"newPassword\":\"validpass1\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirm_shortPassword_is400() throws Exception {
    mvc.perform(post("/api/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"any\",\"newPassword\":\"x\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirm_validToken_is204_andNewLoginWorks() throws Exception {
    User u = new User();
    u.setUsername("ctrl-" + System.nanoTime());
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash(passwordEncoder.encode("oldpass123"));
    userRepository.save(u);

    String raw = "raw-token-" + System.nanoTime();
    PasswordResetToken t =
        PasswordResetToken.builder()
            .tokenHash(TokenHashing.sha256Hex(raw))
            .userId(u.getId())
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build();
    tokenRepository.save(t);

    mvc.perform(post("/api/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + raw + "\",\"newPassword\":\"newpass123\"}"))
        .andExpect(status().isNoContent());

    // Login with new password succeeds.
    MvcResult login =
        mvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"newpass123\"}"))
            .andReturn();
    assertThat(login.getResponse().getStatus()).isEqualTo(200);
  }
}
```

- [ ] **Step 2.10: Compile + run tests**

Run:

```bash
cd /src/ai_hakaton/backend && ./gradlew compileJava compileTestJava test
```

Expected: `BUILD SUCCESSFUL`. All prior tests still pass. The new tests pass (≈13 new).

- [ ] **Step 2.11: Commit**

```bash
git -C /src/ai_hakaton add backend/src/main/resources/db/migration/V10__password_reset_tokens.sql \
    backend/src/main/java/com/hackathon/features/passwordreset/ \
    backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java \
    backend/src/test/java/com/hackathon/features/passwordreset/
git -C /src/ai_hakaton commit -m "feat(password-reset): backend endpoints + token store + email" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Frontend — request + confirm pages, LoginPage link, routing

**Goal:** Public `/forgot-password` page, public `/reset-password` page, "Forgot password?" link on `/login`, success banner on login after a reset, page tests, roadmap mark-complete.

**Files:**
- Create: `frontend/src/services/passwordResetService.ts`
- Create: `frontend/src/pages/ForgotPasswordPage.tsx`
- Create: `frontend/src/pages/ResetPasswordPage.tsx`
- Create: `frontend/src/pages/__tests__/ForgotPasswordPage.test.tsx`
- Create: `frontend/src/pages/__tests__/ResetPasswordPage.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx`
- Modify: `FEATURES_ROADMAP.md`

- [ ] **Step 3.1: Add the service**

Create `frontend/src/services/passwordResetService.ts`:

```ts
import axios from 'axios';

export const passwordResetService = {
  async request(email: string): Promise<void> {
    await axios.post('/api/password-reset/request', { email });
  },
  async confirm(token: string, newPassword: string): Promise<void> {
    await axios.post('/api/password-reset/confirm', { token, newPassword });
  },
};
```

- [ ] **Step 3.2: Add `ForgotPasswordPage`**

Create `frontend/src/pages/ForgotPasswordPage.tsx`:

```tsx
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { passwordResetService } from '../services/passwordResetService';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await passwordResetService.request(email);
    } catch {
      // Swallow — enumeration-safe response is always 204, network error is rare.
    } finally {
      setSubmitted(true);
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4">
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-md">
        <h1 className="text-2xl font-bold mb-6 text-center">Forgot password</h1>
        {submitted ? (
          <div className="text-sm text-gray-700">
            If an account exists for that email, we sent a reset link. Check your inbox.
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="email" className="block text-sm font-medium mb-1">
                Email
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              type="submit"
              disabled={busy}
              className="w-full px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400"
            >
              {busy ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        )}
        <p className="text-sm text-center mt-6 text-gray-600">
          <Link to="/login" className="text-blue-500 hover:underline">
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 3.3: Add `ResetPasswordPage`**

Create `frontend/src/pages/ResetPasswordPage.tsx`:

```tsx
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { passwordResetService } from '../services/passwordResetService';

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [pw, setPw] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4">
        <div className="bg-white rounded-lg shadow p-8 w-full max-w-md">
          <h1 className="text-2xl font-bold mb-4 text-center">Invalid link</h1>
          <p className="text-sm text-gray-700 mb-4">
            This reset link is missing its token. Request a fresh one.
          </p>
          <Link to="/forgot-password" className="text-blue-500 hover:underline">
            Request a new link
          </Link>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (pw.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    if (pw !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    setBusy(true);
    try {
      await passwordResetService.confirm(token, pw);
      navigate('/login', { replace: true, state: { passwordResetSuccess: true } });
    } catch {
      setError('Link is invalid or expired. Request a new one.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4">
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-md">
        <h1 className="text-2xl font-bold mb-6 text-center">Set a new password</h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="pw" className="block text-sm font-medium mb-1">
              New password
            </label>
            <input
              id="pw"
              type="password"
              value={pw}
              onChange={(e) => setPw(e.target.value)}
              required
              minLength={8}
              className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label htmlFor="confirm" className="block text-sm font-medium mb-1">
              Confirm password
            </label>
            <input
              id="confirm"
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
              minLength={8}
              className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          {error && <div className="text-sm text-red-600">{error}</div>}
          <button
            type="submit"
            disabled={busy}
            className="w-full px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400"
          >
            {busy ? 'Saving…' : 'Save new password'}
          </button>
        </form>
      </div>
    </div>
  );
}
```

- [ ] **Step 3.4: Wire the routes in `App.tsx`**

In `frontend/src/App.tsx`, add the two public routes above the `<Route element={<AuthGuard>...` block. Import the pages at the top and add the routes:

```tsx
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
```

and inside `<Routes>`:

```tsx
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
```

Place these before the `/dashboard` redirect and before the AuthGuard-wrapped block so they stay public.

- [ ] **Step 3.5: Add the "Forgot password?" link + success banner to `LoginPage`**

Modify `frontend/src/pages/LoginPage.tsx`:

1. Import `useLocation` alongside the other react-router imports:

```tsx
import { Link, useNavigate, useLocation } from 'react-router-dom';
```

2. At the top of the component body, read the success flag:

```tsx
  const location = useLocation();
  const resetSuccess =
    (location.state as { passwordResetSuccess?: boolean } | null)?.passwordResetSuccess === true;
```

3. Render the banner just under the `<h1>` title:

```tsx
        {resetSuccess && (
          <div className="text-green-700 bg-green-50 border border-green-200 rounded px-3 py-2 text-sm mb-4">
            Password changed — sign in with your new password.
          </div>
        )}
```

4. Add the "Forgot password?" link right above the existing "No account? Sign up" line:

```tsx
        <p className="text-sm text-center mt-4 text-gray-600">
          <Link to="/forgot-password" className="text-blue-500 hover:underline">
            Forgot password?
          </Link>
        </p>
```

- [ ] **Step 3.6: Test `ForgotPasswordPage`**

Create `frontend/src/pages/__tests__/ForgotPasswordPage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ForgotPasswordPage from '../ForgotPasswordPage';
import { passwordResetService } from '../../services/passwordResetService';

vi.mock('../../services/passwordResetService', () => ({
  passwordResetService: { request: vi.fn().mockResolvedValue(undefined), confirm: vi.fn() },
}));

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits email and shows success message', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText(/email/i), 'alice@x.test');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(passwordResetService.request).toHaveBeenCalledWith('alice@x.test');
    await waitFor(() =>
      expect(screen.getByText(/we sent a reset link/i)).toBeInTheDocument(),
    );
  });

  it('still shows success banner when backend errors (enumeration-safe)', async () => {
    (passwordResetService.request as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error('boom'),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText(/email/i), 'alice@x.test');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    await waitFor(() =>
      expect(screen.getByText(/we sent a reset link/i)).toBeInTheDocument(),
    );
  });
});
```

- [ ] **Step 3.7: Test `ResetPasswordPage`**

Create `frontend/src/pages/__tests__/ResetPasswordPage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ResetPasswordPage from '../ResetPasswordPage';
import { passwordResetService } from '../../services/passwordResetService';

vi.mock('../../services/passwordResetService', () => ({
  passwordResetService: { request: vi.fn(), confirm: vi.fn().mockResolvedValue(undefined) },
}));

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ResetPasswordPage />
    </MemoryRouter>,
  );
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows an error when token query param is missing', () => {
    renderAt('/reset-password');
    expect(screen.getByText(/missing its token/i)).toBeInTheDocument();
  });

  it('submits token + new password on save', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc');

    await user.type(screen.getByLabelText(/new password/i), 'newpass123');
    await user.type(screen.getByLabelText(/confirm password/i), 'newpass123');
    await user.click(screen.getByRole('button', { name: /save new password/i }));

    await waitFor(() =>
      expect(passwordResetService.confirm).toHaveBeenCalledWith('abc', 'newpass123'),
    );
  });

  it('shows mismatched-password error without calling the service', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc');

    await user.type(screen.getByLabelText(/new password/i), 'newpass123');
    await user.type(screen.getByLabelText(/confirm password/i), 'different1');
    await user.click(screen.getByRole('button', { name: /save new password/i }));

    expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    expect(passwordResetService.confirm).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 3.8: Update the roadmap**

In `/src/ai_hakaton/FEATURES_ROADMAP.md`, find the Feature #9 (Password Reset) entry. Flip its status to `✅ COMPLETE` with completion date `2026-04-20`. Follow the pattern already used for Features #7 and #12.

- [ ] **Step 3.9: Build the frontend**

Run:

```bash
cd /src/ai_hakaton/frontend && npm run build
```

Expected: `tsc && vite build` succeeds with no TypeScript errors.

- [ ] **Step 3.10: Run frontend tests**

Run:

```bash
cd /src/ai_hakaton/frontend && npm test -- --run
```

Expected: all previous suites pass + 5 new test cases (2 in ForgotPasswordPage, 3 in ResetPasswordPage).

- [ ] **Step 3.11: Smoke-test in Docker**

Run:

```bash
docker compose up -d --build backend frontend mailhog
```

In a browser:
1. Open `http://localhost:5173/login`. Click "Forgot password?".
2. On the forgot-password page, enter an email that corresponds to an existing user. Submit. See the "we sent a reset link" message.
3. Open `http://localhost:8025` (MailHog UI). See the reset email arrive.
4. Copy the link from the email into the browser. See the "Set a new password" page.
5. Enter two matching passwords ≥ 8 chars. Submit. Expect redirect to `/login` with the green "Password changed" banner.
6. Log in with the new password. Expect success.

If the smoke test reveals issues, fix them inline before committing.

- [ ] **Step 3.12: Commit**

```bash
git -C /src/ai_hakaton add frontend/src/services/passwordResetService.ts \
    frontend/src/pages/ForgotPasswordPage.tsx \
    frontend/src/pages/ResetPasswordPage.tsx \
    frontend/src/pages/__tests__/ForgotPasswordPage.test.tsx \
    frontend/src/pages/__tests__/ResetPasswordPage.test.tsx \
    frontend/src/App.tsx \
    frontend/src/pages/LoginPage.tsx \
    FEATURES_ROADMAP.md
git -C /src/ai_hakaton commit -m "feat(password-reset): frontend pages + LoginPage link + roadmap" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Notes

- **Spec coverage:** Every spec section maps to tasks 1–3. Infrastructure (§4) ↔ Task 1. Data model + API + components (§2–3) ↔ Task 2. Frontend (§5) ↔ Task 3. Testing (§6) embedded in tasks 2–3.
- **Green commits:** Task 1 only adds infrastructure (no feature wiring), compiles cleanly. Task 2 lands migration + code + tests together — the test profile's `NoopMailSender` keeps CI green without SMTP. Task 3 ships self-contained frontend.
- **Placeholder scan:** No TBDs, no "add appropriate error handling", no "similar to Task N". All code blocks complete.
- **Type consistency:** `tokenHash` is SHA-256 hex everywhere. Endpoints are `/api/password-reset/request` and `/api/password-reset/confirm` throughout. `MIN_PASSWORD_LENGTH = 8` matches the existing `UserService.changePassword` value.
