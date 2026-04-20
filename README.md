# AI Hakaton Chat

Hackathon chat app: rooms, direct messages, friendships, invitations, presence,
reactions, attachments, unread indicators, password reset, multi-session
management, light/dark theme.

## Stack

- Backend: Spring Boot 3.5, Java 25, PostgreSQL, Redis, MinIO (S3)
- Frontend: React 19, Vite, TypeScript, Tailwind
- Realtime: WebSocket + STOMP over SockJS
- Mail: MailHog (dev SMTP sink)

## Running locally

```bash
docker compose up -d
```

Services:

| Service    | URL                       |
|------------|---------------------------|
| Frontend   | http://localhost:3000     |
| Backend    | http://localhost:8080     |
| PostgreSQL | localhost:5432            |
| Redis      | localhost:6379            |
| MinIO      | http://localhost:9001     |
| **MailHog**| **http://localhost:8025** |

## Where to find the password-reset link

The backend sends reset emails through MailHog's local SMTP sink (port `1025`);
real SMTP delivery is **not** configured in dev. To retrieve a reset link:

1. Go to [http://localhost:8025](http://localhost:8025) — the MailHog web UI.
2. Open the latest "Reset your password" email addressed to the account that
   requested the reset (from `/forgot-password`).
3. Click (or copy) the reset link. It points at
   `http://localhost:3000/reset-password?token=...` and expires after 30 minutes.

MailHog keeps messages in memory only — restarting the `chat-mailhog` container
clears the inbox.

## Other useful local URLs

- Actuator health: http://localhost:8080/actuator/health
- MinIO console: http://localhost:9001 (user/password: `minioadmin` / `minioadmin`)

## Tests

Unit & integration:

```bash
cd backend && ./gradlew test
cd frontend && npm test
```

End-to-end (requires stack running):

```bash
cd frontend && npm run test:e2e
```
