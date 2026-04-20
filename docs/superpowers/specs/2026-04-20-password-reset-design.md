# Password Reset — Design Spec

**Date:** 2026-04-20
**Status:** design approved, pending plan

## Goal

Let a user who has forgotten their password recover access via an email-linked reset flow. Submits the requirement in PDF §2.1.4 ("password reset").

A successful reset replaces the password hash, invalidates any live JWT sessions the user had, and kicks every open tab via the existing eviction channel. The endpoint is resistant to account-enumeration probing and to email-bomb abuse.

## Scope

In scope:
- `POST /api/password-reset/request` — generate a time-limited opaque token, email the user a reset link.
- `POST /api/password-reset/confirm` — verify the token, set a new password, revoke live sessions.
- Frontend: "Forgot password?" link on `/login`, `/forgot-password` request page, `/reset-password` confirmation page.
- MailHog service in `docker-compose.yml` for a local SMTP sink.

Out of scope:
- Email verification during registration.
- Two-factor / TOTP.
- CAPTCHA on the request endpoint.
- Per-IP rate limiting (per-user cooldown only; IP rate-limiting deferred — needs proper `X-Forwarded-For` handling we don't have yet).
- Production email provider integration (MailHog is dev-only; production would set `SPRING_MAIL_*` env vars to SendGrid/etc).

## Architecture

A new feature package `com.hackathon.features.passwordreset` owns both endpoints plus the email builder. Persistence is a new Postgres table `password_reset_tokens` owning `(token_hash, user_id, expires_at, used_at)`. SMTP transport is the Spring `JavaMailSender` starter pointing at a new MailHog container.

The confirmation path reuses Feature #12 infrastructure unchanged: once the password is updated, the service iterates `PresenceService.listSessions(userId)`, revokes each `tokenHash` via `TokenRevocationService.revoke(..., 24h)`, and pushes `EVICTED` frames via `SessionDisconnector.disconnect(sessionId)` + `SimpMessagingTemplate.convertAndSendToUser(...)`. Any open tabs redirect themselves to `/login` via the existing `useEvictedSessionWatcher` hook.

No JWT-shape changes. No new WebSocket destinations. No new frontend global state.

## Data model

### Migration `V10__password_reset_tokens.sql`

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

- `token_hash` is the SHA-256 hex digest of the raw token. The raw token is never persisted server-side.
- `user_id` cascades on account deletion (matches V7 pattern).
- The partial index on non-used tokens powers the per-user cooldown check.

### Raw token

- 32 bytes from `SecureRandom`, Base64-URL encoded (43 chars, URL-safe).
- Entropy: 256 bits.
- Sent to the user's email address; hashed with SHA-256 before any server-side comparison.

### Token lifetime

- `expires_at = created_at + 30 minutes`.
- Enforced at confirm time (`expires_at < NOW()` → 400).

### Per-user cooldown

- On `request`, reject silently (still return 204) if any row exists for this user where `used_at IS NULL AND created_at > NOW() - INTERVAL '2 minutes'`.
- Prevents email-bomb abuse. Does not leak the cooldown state to the caller — identical 204 either way.

## Backend API

Both endpoints are public (no authentication). Both require `SecurityConfig` to explicitly permit them alongside `/api/users/register` and `/api/users/login`.

### `POST /api/password-reset/request`

Request:
```json
{ "email": "alice@x.test" }
```

Response: always `204 No Content`.

Service steps:
1. Look up user by email. Unknown → 204 (enumeration protection).
2. Cooldown check: fresh unused token newer than 2 min → 204 without sending.
3. Generate 32-byte raw token via `SecureRandom`, base64url-encode.
4. Compute SHA-256 hex. Insert `password_reset_tokens` row: `user_id`, `token_hash`, `expires_at = NOW() + 30 min`.
5. Build MIME email (plain-text + simple HTML) with link `${app.frontend-base-url}/reset-password?token=<raw>`. Send via `JavaMailSender`.
6. Return 204.

### `POST /api/password-reset/confirm`

Request:
```json
{ "token": "<raw base64url>", "newPassword": "newpass123" }
```

Response:
- `204 No Content` on success.
- `400 Bad Request` when the token is missing / unknown / expired / already used, or when the new password fails validation.

Service steps:
1. Compute SHA-256 of the supplied token. `SELECT` by `token_hash`.
2. Reject if missing, `used_at IS NOT NULL`, or `expires_at < NOW()`.
3. Validate new password (length ≥ 8; reuse the existing `UserService.updatePassword` validator).
4. Update `user.password_hash` via the existing `PasswordEncoder`.
5. Stamp `used_at = NOW()` on the token row.
6. For each session in `PresenceService.listSessions(user_id)`:
   - `TokenRevocationService.revoke(tokenHash, 24h)`.
   - Push `EvictedEvent("EVICTED", sessionId)` to `/user/{userId}/queue/sessions`.
   - `SessionDisconnector.disconnect(sessionId)`.
   - `PresenceService.markOffline(userId, sessionId)`.
7. Return 204.

### Security posture

- Both endpoints permitted anonymously in `SecurityConfig`.
- Token compared by exact equality on SHA-256 hash (WHERE `token_hash = :hash`).
- Responses never echo the token or the password.
- On success, all existing JWTs for that user are revoked — matches the "account may be compromised" threat model.

### Components

| File | Responsibility |
|---|---|
| `PasswordResetToken.java` | JPA entity mirroring the table |
| `PasswordResetTokenRepository.java` | Spring Data repo with `findByTokenHash`, `existsActiveForUser(userId, cutoff)` |
| `PasswordResetService.java` | Orchestrates the 7 confirm steps and the 6 request steps; injects `UserRepository`, `UserService`, `PasswordEncoder`, `JavaMailSender`, `PresenceService`, `TokenRevocationService`, `SessionDisconnector`, `SimpMessagingTemplate`, `SecureRandom` |
| `PasswordResetController.java` | Two `@PostMapping` endpoints; thin REST adapter |
| `PasswordResetEmailBuilder.java` | Builds `MimeMessage`; subject/body templates |
| `NoopMailSender.java` | `@Profile("test")` stub `JavaMailSender` so tests never hit SMTP |

## Configuration and email transport

### `docker-compose.yml` addition

```yaml
  mailhog:
    image: mailhog/mailhog:v1.0.1
    container_name: chat-mailhog
    ports:
      - "1025:1025"
      - "8025:8025"
```

Backend service gets:

```yaml
SPRING_MAIL_HOST: mailhog
SPRING_MAIL_PORT: 1025
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "false"
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "false"
APP_FRONTEND_BASE_URL: "http://localhost:5173"
APP_MAIL_FROM: "noreply@chat.local"
```

### `build.gradle`

Add `implementation 'org.springframework.boot:spring-boot-starter-mail'`.

### `application.yml`

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

### `application-test.yml`

Test profile short-circuits via a `@Profile("test") @Service class NoopMailSender implements JavaMailSender` that no-ops every method. Matches the `NoopTokenRevocationService` / `NoopPresencePublisher` pattern.

### Email content

```
From: ${app.mail-from}
To:   <user email>
Subject: Reset your password

You (or someone else) requested a password reset for your account.

To set a new password, open this link within 30 minutes:
  ${app.frontend-base-url}/reset-password?token=<raw>

If you didn't request this, you can ignore this email — your password stays unchanged.
```

Simple HTML version mirrors the plain text with an `<a>` tag on the link. Built via `MimeMessageHelper.setText(plainText, html)`.

## Frontend

### Routing (public — outside `AuthGuard`)

- `/forgot-password` → `ForgotPasswordPage`
- `/reset-password` → `ResetPasswordPage`

### New files

- `frontend/src/services/passwordResetService.ts` — `request(email)`, `confirm(token, newPassword)`.
- `frontend/src/pages/ForgotPasswordPage.tsx` — email input, submit, always shows "If an account exists for that email, we sent a reset link. Check your inbox." regardless of backend response. Back-link to `/login`.
- `frontend/src/pages/ResetPasswordPage.tsx` — reads `token` from `useSearchParams`. If missing → error + link to `/forgot-password`. Otherwise two password inputs (new, confirm), client-side check for match and length ≥ 8. On submit: success → `navigate('/login', { state: { passwordResetSuccess: true } })`. 400 → "Link is invalid or expired. Request a new one." with link back to `/forgot-password`.

### Modified files

- `frontend/src/App.tsx` — register the two new public routes.
- `frontend/src/pages/LoginPage.tsx` — "Forgot password?" link under the sign-in button. When `location.state?.passwordResetSuccess`, render a green banner "Password changed — sign in with your new password."

### Tests

- `ForgotPasswordPage.test.tsx` — render + type email + submit → service called with the email; success message appears.
- `ResetPasswordPage.test.tsx` — render with `?token=abc` → two matching passwords → submit → service called with `(abc, newPass)`; also test missing-token and mismatched-confirm paths.

## Testing

All backend tests use `@SpringBootTest @ActiveProfiles("test")` with Testcontainers Postgres and the noop mail sender.

### `PasswordResetServiceTest`

- `request_knownEmail_persistsTokenAndSendsEmail` — one row in `password_reset_tokens`, `JavaMailSender.send` called once with subject/body containing the raw token.
- `request_unknownEmail_isSilentNoOp` — no row inserted, no mail sent, still 204.
- `request_whenRecentTokenExists_skipsSecondMail` — two requests within 30s → one mail, one active row.
- `confirm_validToken_updatesPassword_marksUsed_revokesSessions` — `passwordHash` matches new password (not old); `used_at` stamped; `TokenRevocationService.revoke` called for each session's `tokenHash`; `SessionDisconnector.disconnect` called per session id; `PresenceService.markOffline` called per session.
- `confirm_expiredToken_returns400` — manually back-date `expires_at`.
- `confirm_usedToken_returns400` — pre-stamp `used_at`.
- `confirm_unknownToken_returns400` — random token string.

### `PasswordResetControllerTest`

MockMvc integration tests matching the `SessionControllerTest` pattern:

- `POST /api/password-reset/request` → 204 for any email payload.
- `POST /api/password-reset/confirm` with invalid/expired/used token → 400.
- `POST /api/password-reset/confirm` with valid token → 204 and password changes (verified via a follow-up login call).

### Green-commit discipline

1. **Infrastructure**: `build.gradle` mail-starter + `docker-compose.yml` MailHog + `application.yml` + `application-test.yml` + `NoopMailSender` — one commit.
2. **Backend feature**: migration + entity + repo + service + controller + email builder + `SecurityConfig` edit + all backend tests — one commit.
3. **Frontend**: service + two pages + `App.tsx` routes + `LoginPage` link + success banner + page tests — one commit.

Each commit compiles on its own and leaves the build green.

## Open items

None — all design questions resolved during brainstorming.
