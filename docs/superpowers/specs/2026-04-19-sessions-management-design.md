# Feature #12 — Sessions Management Design

**Date:** 2026-04-19
**Status:** design approved, pending plan

## Goal

Let a user see every active WebSocket session tied to their account (browser user-agent, IP, connected-at, last-seen) and forcibly end any of them — either one at a time ("Log out this device") or in bulk ("Log out everywhere else"). Ending a session must both close the live socket AND revoke the JWT so the old token cannot reconnect.

This feature is the second half of the original Feature #7; presence shipped separately. The Redis infrastructure presence introduced (per-session hash, instance-owned entries, connect/disconnect event hooks) is the substrate sessions extend.

## Scope

In scope:
- List active WS sessions for the current user.
- End a single session (server-side disconnect + JWT revocation).
- End all sessions except the current one.
- Revoked JWT cannot be reused — REST or WS reconnect both rejected.

Out of scope:
- User-agent parsing / geo-IP lookup (raw strings shown as-is).
- Login-history tracking (only live WS sessions are shown).
- Admin view of other users' sessions.
- Password-change-triggered mass revocation (covered by Feature #8 if needed).

## Architecture

Sessions management extends presence rather than paralleling it. The existing `presence:{userId}` Redis hash gains three identifying fields plus a JWT token hash on each session entry. A new `com.hackathon.features.sessions` package owns the user-facing API (list/logout/logout-others). A second Redis namespace `revoked_token:{hash}` holds revoked-JWT markers with TTL equal to the JWT's remaining lifetime, so the store self-prunes.

Server-initiated WS disconnect goes through a small `SessionDisconnector` component that pushes a STOMP DISCONNECT frame onto `clientOutboundChannel`. Revocation is enforced at two checkpoints: `JwtAuthenticationFilter` for REST, and the existing WS CONNECT `ChannelInterceptor` for reconnect attempts.

No new Postgres migration. No JWT issuance changes — we hash the raw token string (SHA-256) rather than introducing a jti claim.

## Data model

### Extended `SessionEntry` (Redis hash value, JSON)

```
{
  idle: boolean,
  lastSeen: long (epoch millis),
  instance: string (server instance id),
  userAgent: string | null,
  remoteAddr: string | null,
  connectedAt: long (epoch millis),
  tokenHash: string (SHA-256 hex of raw JWT)
}
```

- `userAgent` / `remoteAddr` are nullable because the handshake may not carry them (defensive).
- `connectedAt` = `lastSeen` at `markOnline` time; never updated thereafter.
- `tokenHash` is the session-owning JWT's SHA-256 hex. Used as the revocation set key.

### Revocation store

One Redis key per revoked JWT: `revoked_token:{sha256HexOfJwt}` = `"1"`, TTL = remaining JWT lifetime in seconds (from the JWT `exp` claim). Keys self-expire — no sweeper. A per-entry TTL is preferred over a single set with collective TTL because tokens have varying remaining lifetimes.

### `PresenceService` signature changes

```java
void markOnline(UUID userId, String sessionId, String userAgent, String remoteAddr, String tokenHash);
List<SessionView> listSessions(UUID userId);
```

`heartbeat` / `markActive` / `markAfk` preserve userAgent/remoteAddr/connectedAt/tokenHash on update (only `idle` and `lastSeen` change).

`SessionView` is a DTO record mirroring `SessionEntry` plus the session id (which lives as the hash field name in Redis).

## Backend API

All endpoints require auth; all are scoped to `currentUserId` (no cross-user access).

### `GET /api/sessions`
Returns the current user's active WS sessions.

Response 200:
```
[
  {
    sessionId: "abc123",
    userAgent: "Mozilla/5.0 ..." | null,
    remoteAddr: "10.0.0.4" | null,
    connectedAt: "2026-04-19T10:23:11Z",
    lastSeen:    "2026-04-19T10:47:02Z",
    idle: false,
    current: true
  },
  ...
]
```

`current` is true iff the row's `sessionId` equals the request's `X-Session-Id` header. Missing header → every row gets `current: false`.

### `DELETE /api/sessions/{sessionId}`
Log out one session.

1. Load entry from Redis; 404 if not found under this user.
2. `SET revoked_token:{tokenHash}` with TTL = remaining JWT seconds.
3. Send `{type: 'EVICTED', sessionId}` to `/user/{uuid}/queue/sessions` (cooperative client-side toast + self-disconnect).
4. `SessionDisconnector.disconnect(sessionId)` — hard server-initiated close.
5. `presenceService.markOffline(userId, sessionId)`.

Response: 204.

### `DELETE /api/sessions/others`
Log out all sessions except the current one.

- 400 if `X-Session-Id` header missing.
- Iterates every session whose id ≠ current, applies the same steps 2–5 per entry.
- Response 204 with body `{revokedCount: N}`.

### Controller / service layout

- `SessionController` — thin REST adapter, inner-record DTOs per project convention.
- `SessionService` — orchestrates listing, revocation, disconnect, presence cleanup.
- `SessionDisconnector` — `@Component` wrapping `@Qualifier("clientOutboundChannel") MessageChannel` + `SimpUserRegistry`.
- `TokenRevocationService` — interface with two implementations:
  - `RedisTokenRevocationService` (`@Profile("!test")`) — `revoke(hash, ttlSeconds)`, `isRevoked(hash)`.
  - `NoopTokenRevocationService` (`@Profile("test")`) — no-op revoke, always returns `false` for `isRevoked`.

## WebSocket integration

### Capturing metadata on CONNECT

Two interceptors:

1. **`HandshakeInterceptor`** (new): stashes `request.getRemoteAddr()` and `request.getHeader("User-Agent")` into the handshake `attributes` map on `beforeHandshake`.
2. **`ChannelInterceptor`** on `clientInboundChannel` (existing, extended): on CONNECT frame, reads `userAgent` / `remoteAddr` from `accessor.getSessionAttributes()`, computes `tokenHash = sha256Hex(rawJwt)`, stores all three back into session attributes.

### Rejecting revoked tokens on reconnect

Same CONNECT branch, before the success path: `if (tokenRevocationService.isRevoked(sha256Hex(token))) throw MessageDeliveryException`. SockJS surfaces this as a connect failure; the existing frontend 401 handling redirects to login.

### Surfacing metadata to presence

`PresenceEventListener.handleSessionConnected` reads the three fields from the session attributes on `SessionConnectedEvent` and passes them into the extended `markOnline`.

### Server-initiated disconnect

```java
StompHeaderAccessor a = StompHeaderAccessor.create(StompCommand.DISCONNECT);
a.setSessionId(sessionId);
clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], a.getMessageHeaders()));
```

Spring closes the socket after flushing. Safe if the session is already gone (no-op).

### REST revocation check

`JwtAuthenticationFilter` adds one guard after the existing user-existence check:

```java
if (tokenRevocationService.isRevoked(sha256Hex(token))) { respond 401; return; }
```

Test profile's `NoopTokenRevocationService` returns false so existing tests stay green.

## Frontend

### Session id capture

`websocketService` exposes `getSessionId(): string | null`, set from the STOMP CONNECTED frame's `session` header (stompjs passes it as the first callback arg).

### Axios interceptor

`services/api.ts` request interceptor sets `X-Session-Id: <id>` when available. Powers the `current` flag and `/others`.

### New files

- `services/sessionsService.ts` — `list()`, `logout(sessionId)`, `logoutOthers()`.
- `pages/SessionsPage.tsx` — replaces `SessionsStub.tsx`.

### `SessionsPage` UX (B from Q6)

- Header: "Active sessions".
- "Log out everywhere else" button top-right; disabled when list length ≤ 1; confirm dialog before firing.
- Table columns: User-Agent, IP, Connected, Last seen, action column ("This device" badge OR "Log out" button).
- Polls every 30s; reloads after any mutation.

### Eviction queue subscription

Installed in `AppShell` (alongside `useAfkTracking`): subscribe to `/user/{uuid}/queue/sessions`. On `{type: 'EVICTED', sessionId}`:
- If `sessionId === websocketService.getSessionId()` → toast "You were logged out from another device", clear `authToken`, redirect to `/login`.
- Else → no-op (page polling picks up the change).

### Routing

`/sessions` route already registered in `App.tsx`; swap the stub component for `SessionsPage`.

## Testing

Per Q7 answer A — unit/integration tests, no STOMP-level WS test.

### Backend

- **`SessionServiceTest`** — list filters to current user; logout removes entry + calls revocation + calls disconnector (both `@MockBean`); logout of foreign/unknown id → 404; `logoutOthers` keeps current, returns `revokedCount`.
- **`SessionControllerTest`** — standard `TestRestTemplate` pattern: 401 unauthenticated, `current` flag wired to `X-Session-Id`, 404/400 paths.
- **`InMemoryPresenceServiceTest`** additions — new fields round-trip through `markOnline` and are preserved across `heartbeat`/`markActive`/`markAfk`.
- **`JwtAuthenticationFilterTest`** additions — revoked token → 401 with `TokenRevocationService` stubbed to "revoked".
- **`SessionDisconnectorTest`** — asserts a STOMP DISCONNECT with the right session id is posted to a mocked `clientOutboundChannel`.

### Frontend

- Render smoke test for `SessionsPage` (list + buttons appear).
- No dedicated `sessionsService` test.

### Green-commit discipline

- `SessionEntry` field addition + `markOnline` signature change + both presence impls + `PresenceEventListener` edit + presence test updates → one commit.
- `TokenRevocationService` interface + both profile beans + `JwtAuthenticationFilter` edit + filter test → one commit.
- `SessionDisconnector` + `SessionService` + `SessionController` + tests → one commit.
- `HandshakeInterceptor` + `WebSocketConfig` wiring + revocation check on CONNECT → one commit.
- Frontend `sessionsService` + `SessionsPage` + axios interceptor + eviction queue subscription → one commit.

Each commit compiles on its own and leaves the build green.

## Open items

None — all design questions resolved during brainstorming.
