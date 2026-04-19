# Feature #7 (presence): User Presence — Design

**Status:** approved 2026-04-19
**Scope:** multi-instance-safe per-user presence (online / AFK / offline) propagated live over WebSocket + a REST snapshot endpoint. Drives the existing presence-dot placeholders in `SideTreeContactList` and `RoomMembersPanel`.
**Split from Feature #7 (original):** active session list + "log out from this session" action is split into Feature #12 and NOT included here.

## Goal

1. Every connected client reflects live `ONLINE / AFK / OFFLINE` state for every friend (in the side tree contacts list) and for every current room-member (grouped in the right panel).
2. A user is `ONLINE` while any of their WebSocket sessions reports activity within the last 60 seconds. `AFK` when all sessions have been inactive for ≥ 60 seconds but at least one is still connected. `OFFLINE` when no sessions are connected.
3. Multi-instance safe — any backend replica handles any WS connection; presence state is shared via Redis.

## Non-goals

| Out of scope | Why / where |
|---|---|
| Sessions management page (list + "log out from this session") | Feature #12 — security-adjacent, doesn't belong bundled with realtime presence |
| Last-seen-at display | Current state only |
| Typing indicators | Separate feature surface |
| Custom statuses ("DND", "In a meeting") | Three states only |
| Presence for non-friends | Not surfaced in UI; `/api/presence` accepts any id for hackathon |
| Authorization filter on presence queries | Not a hackathon blocker |

## Infrastructure

### Docker compose — new service + volume

```yaml
redis:
  image: redis:7-alpine
  container_name: chat-redis
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
  networks:
    - chat-network
```

Top-level `volumes:` block adds `redis_data:`. Backend gets a new env var `SPRING_DATA_REDIS_HOST: redis`.

### Backend dependency

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

(Includes Lettuce client by default.)

### `application.yml`

```yaml
spring:
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: 6379
```

## Presence state machine

Three states: `ONLINE`, `AFK`, `OFFLINE`.

Per-session state (client-reported):
- `ACTIVE` → contributes `ONLINE` to aggregation
- `IDLE` → contributes `AFK` to aggregation
- (a session is either present or absent; absent contributes nothing)

Per-user aggregation:

```
any session ACTIVE → ONLINE
else any session IDLE → AFK
else (no sessions) → OFFLINE
```

Transitions (client-triggered via STOMP):
- On WS connect → server marks the session as `ACTIVE` and records `lastSeen`
- Client sends `/app/presence/afk` → server marks the session as `IDLE`, updates `lastSeen`
- Client sends `/app/presence/active` → server marks the session as `ACTIVE`, updates `lastSeen`
- Client sends `/app/presence/heartbeat` every 30s while connected → server updates `lastSeen` on the existing session state
- On WS disconnect → server removes the session

Server-triggered (watchdog):
- Every 60s, server scans sessions owned by THIS instance. Any session with `lastSeen > 90s ago` is removed (treated as offline — catches suspended laptops + half-closed sockets).

After any change to the per-user session set, the aggregated state is recomputed. If aggregate changed, the server publishes `{ userId, state }` on the Redis channel `presence`. Every instance subscribed to that channel forwards to its local `/topic/presence` STOMP subscribers.

## Redis data model

Single Redis hash per user:

```
key:   presence:{userId}
value: hash of { sessionId → JSON({ state: ACTIVE|IDLE, lastSeen: epoch-millis, instance: <instance-id> }) }
TTL:   none on the hash itself; session entries are removed explicitly on disconnect or by the watchdog
```

Aggregation is computed on read by iterating the hash's values. At hackathon scale (300 simultaneous users, most with 1-2 sessions) this is ~1ms per call. If it ever gets hot, we cache a computed aggregate in a sibling key.

Pub/sub channel: `presence` — payload is JSON `{"userId":"<uuid>","state":"ONLINE|AFK|OFFLINE"}`.

Instance identity: each backend sets `instance = UUID.randomUUID().toString()` at boot. The watchdog iterates `presence:*` keys but only processes session entries where `instance == self`.

## REST API

```
GET /api/presence?userIds=uuid1,uuid2,...
Authorization: Bearer <jwt>

200 → { "uuid1": "ONLINE", "uuid2": "AFK", "uuid3": "OFFLINE" }
401 → without JWT
```

Missing user ids (never connected) return `OFFLINE`. Max 200 ids per request — defensive cap.

## STOMP endpoints

- `/app/presence/afk` — client reports going idle. Empty body.
- `/app/presence/active` — client reports back-active. Empty body.
- `/app/presence/heartbeat` — client periodic ping. Empty body.
- `/topic/presence` — broadcast destination; every connected client subscribes. Payload `{ userId, state }`.

All three inbound mappings read the STOMP session id via `SimpMessageHeaderAccessor.getSessionId()` and the authenticated principal via `Principal.getName()` (which maps to the user's UUID).

## Components

### Backend (new)

```
backend/src/main/java/com/hackathon/features/presence/
  PresenceState.java                    (enum)
  PresenceService.java                  (interface)
  RedisPresenceService.java             (@Profile("!test"))
  InMemoryPresenceService.java          (@Profile("test"))
  PresenceEventListener.java            (@EventListener on Session{Connected,Disconnect})
  PresenceMessageHandler.java           (STOMP @MessageMapping — afk / active / heartbeat)
  PresencePublisher.java                (Redis pub/sub publish wrapper)
  PresenceSubscriber.java               (@Profile("!test"); listens to Redis channel, forwards to /topic/presence)
  PresenceWatchdog.java                 (@Scheduled — 60s sweep of owned sessions)
  PresenceController.java               (GET /api/presence)
```

### Backend (modified)

```
backend/build.gradle                              (+ spring-boot-starter-data-redis)
backend/src/main/resources/application.yml        (+ spring.data.redis.*)
docker-compose.yml                                 (+ redis service + volume + SPRING_DATA_REDIS_HOST env)
```

### Frontend (new)

```
frontend/src/types/presence.ts                    (PresenceState type = 'ONLINE'|'AFK'|'OFFLINE')
frontend/src/services/presenceService.ts          (snapshot via GET /api/presence)
frontend/src/hooks/usePresence.ts                 (snapshot + subscribe + local map)
frontend/src/hooks/useAfkTracking.ts              (inactivity timer + STOMP afk/active/heartbeat)
```

### Frontend (modified)

```
frontend/src/layout/AppShell.tsx                  (install useAfkTracking once per app mount)
frontend/src/layout/SideTreeContactList.tsx       (use usePresence; map state → dot color + icon)
frontend/src/components/RoomMembersPanel.tsx      (use usePresence; reassign members to Online/AFK/Offline buckets)
frontend/src/hooks/useWebSocket.ts                (expose a send() for /app/presence/* and a subscribe helper for /topic/presence)
FEATURES_ROADMAP.md                                (Feature #7 → PRESENCE COMPLETE; split Sessions to #12)
```

## Frontend details

### `useAfkTracking()` — installed once in `AppShell`

- Attaches throttled listeners to `window` for `mousemove`, `keydown`, `touchstart`, `focus`. Throttle to once per 5s — each call updates `lastActivityAt = now`.
- Installs a `setInterval(check, 15_000)`:
  - `idle = now - lastActivityAt >= 60_000`
  - If `idle && currentState !== 'AFK'` → send STOMP `/app/presence/afk`; set `currentState = 'AFK'`.
  - If `!idle && currentState !== 'ACTIVE'` → send `/app/presence/active`; set `currentState = 'ACTIVE'`.
- Installs a separate `setInterval(heartbeat, 30_000)` that sends `/app/presence/heartbeat` while the WS is connected.
- On unmount / sign-out: clears both intervals.
- On WS reconnect: re-sends the current state so server has ground truth.

### `usePresence(userIds: string[])`

- On mount (and when `userIds` changes meaningfully): calls `presenceService.snapshot(userIds)` → seeds an internal `Map<string, PresenceState>`.
- Subscribes to `/topic/presence` once (idempotent — multiple callers share the same subscription via a singleton hook or module-scoped subscription).
- On each incoming `{ userId, state }` event, updates the map in place; if `userIds.includes(userId)`, triggers a re-render.
- Returns `get(userId: string): PresenceState` defaulting to `'OFFLINE'` for unknown ids.

### Rendering

`SideTreeContactList`:

```tsx
const dot = state === 'ONLINE' ? '●' : state === 'AFK' ? '◐' : '○';
const color = state === 'ONLINE' ? 'text-green-500' : state === 'AFK' ? 'text-yellow-500' : 'text-gray-400';
```

`RoomMembersPanel`: partition members by `presence.get(m.userId)` — reuse the existing `renderGroup` helper. Groups update reactively.

## Tests

| Level | Coverage |
|---|---|
| Backend unit — `InMemoryPresenceServiceTest` | Aggregation: 0 sessions → OFFLINE; 1 ACTIVE → ONLINE; 2 ACTIVE → ONLINE; 1 ACTIVE + 1 IDLE → ONLINE; 2 IDLE → AFK; IDLE session removed after watchdog sweep → OFFLINE. |
| Backend unit — `PresenceEventListenerTest` | `SessionConnectedEvent` triggers `markOnline(userId, sessionId)`; `SessionDisconnectEvent` triggers `markOffline`. Mockito-style. |
| Backend unit — `PresenceWatchdogTest` | Session with `lastSeen` > 90s ago → removed; fresh session untouched. |
| Backend unit — `PresenceMessageHandlerTest` | STOMP `/app/presence/afk` calls `markAfk`; `/app/presence/active` calls `markOnline`; `/app/presence/heartbeat` calls `heartbeat`. |
| Backend controller — `PresenceControllerTest` | 200 with snapshot; caps request at 200 ids; 401 without JWT. |
| Frontend unit (vitest) — `usePresence` | Applies snapshot; updates on incoming event; returns `'OFFLINE'` for unknown ids. |
| Frontend unit — `useAfkTracking` | With `vi.useFakeTimers()`: advance 60s with no input → sends `afk`; simulate input → sends `active`. |
| Playwright | Skip — two-browser presence timing is flaky in CI. Browser smoke-test only: two tabs, watch the sidebar dot flip. |

## Security notes

- `/topic/presence` is authenticated (existing WS security). Any logged-in user sees every presence change. Hackathon-acceptable. In production we'd filter "only publish to subscribers who care" (friends, room members). Documented limitation.
- `/api/presence` accepts any user id. No privacy bar.
- Client-reported AFK state is trusted — no server verification. A user could lie ("I'm always AFK" to avoid messages), but the damage is self-inflicted.

## Risk + mitigations

- **Redis unavailability at boot** — backend fails fast (standard Spring Data Redis). Dev: `docker compose up` brings Redis first via `depends_on`. Production: health-check + restart policy.
- **Pub/sub message loss during backend restart** — acceptable: a client will get a fresh snapshot on reconnect; transient inconsistency for ≤ 30s.
- **Horizontal scale to many instances** — each instance's watchdog only sweeps sessions it owns. Redis hash grows linearly with live sessions; ~100 bytes/session → trivial at 300 users.
- **Frontend activity listener overhead** — throttled to 5s; the 15s interval is the only constant cost. Negligible.

## Plan outline

1. Redis in docker-compose + Spring Data Redis dep + application.yml keys
2. `PresenceState` enum + `PresenceService` interface + `InMemoryPresenceService` + `PresenceState` aggregation unit tests
3. `RedisPresenceService` + `PresencePublisher` + `PresenceSubscriber`
4. `PresenceEventListener` (WS lifecycle hook) + test
5. `PresenceMessageHandler` (STOMP mappings) + `PresenceWatchdog` + tests
6. `PresenceController` (GET /api/presence) + tests
7. Frontend types + `presenceService.ts`
8. `usePresence` hook
9. `useAfkTracking` hook + install in `AppShell`
10. Wire `SideTreeContactList` + `RoomMembersPanel`
11. Roadmap update (Presence → COMPLETE; split Sessions to Feature #12)

## Verification checklist

- [ ] `docker compose up -d --build` brings up Redis + backend; backend logs show Redis connection.
- [ ] `./gradlew test` green (new unit tests + no regressions).
- [ ] `npm run build` clean, `npm test -- --run` green.
- [ ] Browser smoke: two tabs as the same user — sidebar of a friend in a third browser shows green dot; idle one tab 70s, yellow; close both tabs, grey.
- [ ] Multi-instance sanity: `docker compose up -d --scale backend=2` (would need sticky sessions OR a load-balancer round-robin, noted as deployment homework). Dev mode uses one instance.
- [ ] `FEATURES_ROADMAP.md` reflects Presence COMPLETE + Feature #12 TODO for Sessions.
