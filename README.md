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
| ejabberd A | `chat-a.local:5222` (c2s), 5269 (s2s), 5280 (admin) |
| ejabberd B | `chat-b.local:5223` (c2s), 5270 (s2s), 5281 (admin) |

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

## Jabber / XMPP federation

The stack ships with two federated [ejabberd](https://www.ejabberd.im/) servers
(`chat-a.local` and `chat-b.local`) so any XMPP client can connect directly —
this satisfies the *advanced* Jabber requirement in the spec including
server-to-server messaging.

**Connect with a Jabber client** (Pidgin, Gajim, Psi, Conversations, Dino, …):

1. Add host entries so your client can resolve the server domains:
   ```
   127.0.0.1  chat-a.local
   127.0.0.1  chat-b.local
   ```
   macOS/Linux: `/etc/hosts` (needs sudo). Windows: `C:\Windows\System32\drivers\etc\hosts`.

2. Use these demo credentials (plaintext auth, STARTTLS disabled in dev):

   | Server | JID                       | Password   | Client port |
   |--------|---------------------------|------------|-------------|
   | A      | `alice@chat-a.local`      | `alicepass`| `5222`      |
   | A      | `admin@chat-a.local`      | `adminpass`| `5222`      |
   | B      | `bob@chat-b.local`        | `bobpass`  | `5223`      |
   | B      | `admin@chat-b.local`      | `adminpass`| `5223`      |

3. Send a message from `alice@chat-a.local` to `bob@chat-b.local` to exercise
   the S2S federation path (ports `5269` / `5270`).

**Admin dashboard**: the Chat app's top menu has a **Jabber** tab
(`/jabber`) showing both servers' reachability, registered/online user counts,
live S2S connection counts, and connection instructions.

## Other useful local URLs

- Actuator health: http://localhost:8080/actuator/health
- MinIO console: http://localhost:9001 (user/password: `minioadmin` / `minioadmin`)
- ejabberd A web admin: http://localhost:5280/admin (`admin@chat-a.local` / `adminpass`)
- ejabberd B web admin: http://localhost:5281/admin (`admin@chat-b.local` / `adminpass`)

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
