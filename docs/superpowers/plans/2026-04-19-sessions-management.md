# Sessions Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can list every active WebSocket session for their account (UA/IP/connected-at/last-seen) and kick one or all-others, with hard JWT revocation so old tokens cannot reconnect.

**Architecture:** Extend the existing Redis `presence:{userId}` hash with UA/IP/connectedAt/tokenHash per session entry. Add a second Redis keyspace `revoked_token:{sha256(jwt)}` (per-key TTL = remaining JWT lifetime) checked by `JwtAuthenticationFilter` and the WS CONNECT interceptor. A small `SessionDisconnector` closes WS sessions via the STOMP outbound channel. Frontend gets a real `/sessions` page replacing the stub.

**Tech Stack:** Spring Boot 3.5 / Java 25 / Spring WebSocket + STOMP / Redis (Lettuce) / React 19 / axios / stompjs.

---

## File Structure

### Backend

**New:**
- `backend/src/main/java/com/hackathon/features/sessions/SessionController.java`
- `backend/src/main/java/com/hackathon/features/sessions/SessionService.java`
- `backend/src/main/java/com/hackathon/features/sessions/SessionDisconnector.java`
- `backend/src/main/java/com/hackathon/features/sessions/SessionView.java`
- `backend/src/main/java/com/hackathon/shared/security/TokenRevocationService.java`
- `backend/src/main/java/com/hackathon/shared/security/RedisTokenRevocationService.java`
- `backend/src/main/java/com/hackathon/shared/security/NoopTokenRevocationService.java`
- `backend/src/main/java/com/hackathon/shared/security/TokenHashing.java`
- `backend/src/main/java/com/hackathon/shared/websocket/SessionAttrHandshakeInterceptor.java`
- `backend/src/test/java/com/hackathon/features/sessions/SessionServiceTest.java`
- `backend/src/test/java/com/hackathon/features/sessions/SessionControllerTest.java`
- `backend/src/test/java/com/hackathon/features/sessions/SessionDisconnectorTest.java`
- `backend/src/test/java/com/hackathon/shared/security/JwtAuthenticationFilterTest.java`

**Modified:**
- `backend/src/main/java/com/hackathon/features/presence/PresenceService.java` (signature)
- `backend/src/main/java/com/hackathon/features/presence/InMemoryPresenceService.java` (extra fields + listSessions)
- `backend/src/main/java/com/hackathon/features/presence/RedisPresenceService.java` (extra fields + listSessions)
- `backend/src/main/java/com/hackathon/features/presence/PresenceEventListener.java` (read session attrs, pass into markOnline)
- `backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java` (register handshake interceptor, capture token+UA+IP into session attrs, check revocation on CONNECT)
- `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java` (revocation check)
- `backend/src/main/java/com/hackathon/shared/security/JwtTokenProvider.java` (expose token expiration epoch millis for TTL calc)
- `backend/src/test/java/com/hackathon/features/presence/InMemoryPresenceServiceTest.java` (extra fields round-trip)

### Frontend

**New:**
- `frontend/src/services/sessionsService.ts`
- `frontend/src/pages/SessionsPage.tsx`
- `frontend/src/hooks/useEvictedSessionWatcher.ts`
- `frontend/src/types/session.ts`

**Modified:**
- `frontend/src/services/websocketService.ts` (capture & expose STOMP session id)
- `frontend/src/services/authService.ts` (add X-Session-Id request interceptor reading from websocketService)
- `frontend/src/App.tsx` (swap stub for real page)
- `frontend/src/layout/AppShell.tsx` (install `useEvictedSessionWatcher`)

**Deleted:**
- `frontend/src/pages/SessionsStub.tsx`

### Roadmap

- `FEATURES_ROADMAP.md` (mark Feature #12 complete in final commit)

---

## Task 1: Extend presence data model with session metadata

**Goal:** `SessionEntry` gains `userAgent`, `remoteAddr`, `connectedAt`, `tokenHash`. `markOnline` takes them as parameters. A new `listSessions` method returns the full metadata per session. Both `PresenceService` impls updated; `PresenceEventListener` still compiles by passing nulls (attr capture happens in Task 2). All presence tests stay green.

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/presence/PresenceService.java`
- Create: `backend/src/main/java/com/hackathon/features/sessions/SessionView.java`
- Modify: `backend/src/main/java/com/hackathon/features/presence/InMemoryPresenceService.java`
- Modify: `backend/src/main/java/com/hackathon/features/presence/RedisPresenceService.java`
- Modify: `backend/src/main/java/com/hackathon/features/presence/PresenceEventListener.java`
- Modify: `backend/src/test/java/com/hackathon/features/presence/InMemoryPresenceServiceTest.java`

- [ ] **Step 1.1: Create `SessionView` DTO**

Create `backend/src/main/java/com/hackathon/features/sessions/SessionView.java`:

```java
package com.hackathon.features.sessions;

import java.time.Instant;

/**
 * Snapshot of a single active WebSocket session belonging to a user.
 * sessionId is the STOMP session id; tokenHash is kept here (not in the REST DTO)
 * because SessionService needs it to drive revocation.
 */
public record SessionView(
    String sessionId,
    String userAgent,
    String remoteAddr,
    Instant connectedAt,
    Instant lastSeen,
    boolean idle,
    String tokenHash) {}
```

- [ ] **Step 1.2: Extend `PresenceService` interface**

Replace `backend/src/main/java/com/hackathon/features/presence/PresenceService.java`:

```java
package com.hackathon.features.presence;

import com.hackathon.features.sessions.SessionView;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PresenceService {
  /**
   * Record a new ACTIVE session for this user. Idempotent per (userId, sessionId).
   * userAgent/remoteAddr may be null when the handshake didn't carry them. tokenHash
   * is the SHA-256 hex of the raw JWT that owns this session.
   */
  void markOnline(
      UUID userId,
      String sessionId,
      String userAgent,
      String remoteAddr,
      String tokenHash);

  /** Flip an existing session to IDLE. No-op if session unknown. */
  void markAfk(UUID userId, String sessionId);

  /** Flip an existing session to ACTIVE. No-op if session unknown (creates it as ACTIVE with null metadata). */
  void markActive(UUID userId, String sessionId);

  /** Update lastSeen for an existing session (from client heartbeats). No-op if unknown. */
  void heartbeat(UUID userId, String sessionId);

  /** Remove a session. */
  void markOffline(UUID userId, String sessionId);

  /** Current aggregated state for a user. OFFLINE when no sessions. */
  PresenceState aggregate(UUID userId);

  /** Batch snapshot for a list of user ids. Missing ids get OFFLINE. */
  Map<UUID, PresenceState> snapshot(List<UUID> userIds);

  /**
   * Evict sessions owned by this instance whose lastSeen is older than cutoff millis.
   * Returns the list of user ids whose session set changed.
   */
  List<UUID> evictStaleOwnedBy(String instanceId, long cutoffEpochMillis);

  /** Return all active sessions for the user (empty list when none). */
  List<SessionView> listSessions(UUID userId);
}
```

- [ ] **Step 1.3: Write failing test for `InMemoryPresenceService` round-trip of new fields**

Append to `backend/src/test/java/com/hackathon/features/presence/InMemoryPresenceServiceTest.java` (inside the existing class):

```java
  @Test
  void listSessionsReturnsMetadataProvidedToMarkOnline() {
    UUID user = UUID.randomUUID();
    service.markOnline(user, "s1", "Chrome/120", "10.0.0.4", "hashA");

    List<com.hackathon.features.sessions.SessionView> rows = service.listSessions(user);

    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.sessionId()).isEqualTo("s1");
    assertThat(row.userAgent()).isEqualTo("Chrome/120");
    assertThat(row.remoteAddr()).isEqualTo("10.0.0.4");
    assertThat(row.tokenHash()).isEqualTo("hashA");
    assertThat(row.idle()).isFalse();
    assertThat(row.connectedAt()).isNotNull();
    assertThat(row.lastSeen()).isNotNull();
  }

  @Test
  void heartbeatAndMarkAfkPreserveMetadata() {
    UUID user = UUID.randomUUID();
    service.markOnline(user, "s1", "Chrome/120", "10.0.0.4", "hashA");
    service.heartbeat(user, "s1");
    service.markAfk(user, "s1");

    var row = service.listSessions(user).get(0);
    assertThat(row.userAgent()).isEqualTo("Chrome/120");
    assertThat(row.remoteAddr()).isEqualTo("10.0.0.4");
    assertThat(row.tokenHash()).isEqualTo("hashA");
    assertThat(row.idle()).isTrue();
  }
```

Add these imports to the top of the test file if not already present:

```java
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 1.4: Run the failing test**

Run:

```bash
cd backend && ./gradlew test --tests 'com.hackathon.features.presence.InMemoryPresenceServiceTest'
```

Expected: FAIL to compile because `listSessions` / new `markOnline` signature don't exist yet.

- [ ] **Step 1.5: Implement `InMemoryPresenceService` changes**

Replace the body of `backend/src/main/java/com/hackathon/features/presence/InMemoryPresenceService.java`:

```java
package com.hackathon.features.presence;

import com.hackathon.features.sessions.SessionView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.Getter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class InMemoryPresenceService implements PresenceService {

  static final class SessionEntry {
    volatile boolean idle;
    volatile long lastSeen;
    final long connectedAt;
    final String instance;
    final String userAgent;
    final String remoteAddr;
    final String tokenHash;

    SessionEntry(
        boolean idle,
        long lastSeen,
        long connectedAt,
        String instance,
        String userAgent,
        String remoteAddr,
        String tokenHash) {
      this.idle = idle;
      this.lastSeen = lastSeen;
      this.connectedAt = connectedAt;
      this.instance = instance;
      this.userAgent = userAgent;
      this.remoteAddr = remoteAddr;
      this.tokenHash = tokenHash;
    }
  }

  private final ConcurrentMap<UUID, ConcurrentMap<String, SessionEntry>> byUser =
      new ConcurrentHashMap<>();

  @Getter private final String instanceId = "test-instance";

  @Override
  public void markOnline(
      UUID userId, String sessionId, String userAgent, String remoteAddr, String tokenHash) {
    long now = System.currentTimeMillis();
    byUser
        .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
        .compute(
            sessionId,
            (k, existing) -> {
              if (existing == null) {
                return new SessionEntry(
                    false, now, now, instanceId, userAgent, remoteAddr, tokenHash);
              }
              existing.idle = false;
              existing.lastSeen = now;
              return existing;
            });
  }

  @Override
  public void markAfk(UUID userId, String sessionId) {
    ConcurrentMap<String, SessionEntry> sessions = byUser.get(userId);
    if (sessions == null) return;
    SessionEntry e = sessions.get(sessionId);
    if (e == null) return;
    e.idle = true;
    e.lastSeen = System.currentTimeMillis();
  }

  @Override
  public void markActive(UUID userId, String sessionId) {
    ConcurrentMap<String, SessionEntry> sessions = byUser.get(userId);
    if (sessions == null || sessions.get(sessionId) == null) {
      markOnline(userId, sessionId, null, null, null);
      return;
    }
    SessionEntry e = sessions.get(sessionId);
    e.idle = false;
    e.lastSeen = System.currentTimeMillis();
  }

  @Override
  public void heartbeat(UUID userId, String sessionId) {
    ConcurrentMap<String, SessionEntry> sessions = byUser.get(userId);
    if (sessions == null) return;
    SessionEntry e = sessions.get(sessionId);
    if (e == null) return;
    e.lastSeen = System.currentTimeMillis();
  }

  @Override
  public void markOffline(UUID userId, String sessionId) {
    ConcurrentMap<String, SessionEntry> sessions = byUser.get(userId);
    if (sessions == null) return;
    sessions.remove(sessionId);
    if (sessions.isEmpty()) byUser.remove(userId);
  }

  @Override
  public PresenceState aggregate(UUID userId) {
    ConcurrentMap<String, SessionEntry> sessions = byUser.get(userId);
    if (sessions == null || sessions.isEmpty()) return PresenceState.OFFLINE;
    return aggregateOf(sessions.values());
  }

  @Override
  public Map<UUID, PresenceState> snapshot(List<UUID> userIds) {
    Map<UUID, PresenceState> out = new HashMap<>();
    for (UUID id : userIds) out.put(id, aggregate(id));
    return out;
  }

  @Override
  public List<UUID> evictStaleOwnedBy(String instanceId, long cutoffEpochMillis) {
    List<UUID> affected = new ArrayList<>();
    for (Map.Entry<UUID, ConcurrentMap<String, SessionEntry>> userEntry : byUser.entrySet()) {
      UUID userId = userEntry.getKey();
      ConcurrentMap<String, SessionEntry> sessions = userEntry.getValue();
      boolean changed = false;
      for (Map.Entry<String, SessionEntry> sessionEntry : sessions.entrySet()) {
        SessionEntry e = sessionEntry.getValue();
        if (e.instance.equals(instanceId) && e.lastSeen < cutoffEpochMillis) {
          sessions.remove(sessionEntry.getKey());
          changed = true;
        }
      }
      if (sessions.isEmpty()) byUser.remove(userId);
      if (changed) affected.add(userId);
    }
    return affected;
  }

  @Override
  public List<SessionView> listSessions(UUID userId) {
    ConcurrentMap<String, SessionEntry> sessions = byUser.get(userId);
    if (sessions == null) return List.of();
    List<SessionView> out = new ArrayList<>(sessions.size());
    for (Map.Entry<String, SessionEntry> entry : sessions.entrySet()) {
      SessionEntry e = entry.getValue();
      out.add(
          new SessionView(
              entry.getKey(),
              e.userAgent,
              e.remoteAddr,
              Instant.ofEpochMilli(e.connectedAt),
              Instant.ofEpochMilli(e.lastSeen),
              e.idle,
              e.tokenHash));
    }
    return out;
  }

  private static PresenceState aggregateOf(Collection<SessionEntry> sessions) {
    boolean anyActive = false;
    boolean anyIdle = false;
    for (SessionEntry s : sessions) {
      if (s.idle) anyIdle = true;
      else anyActive = true;
    }
    if (anyActive) return PresenceState.ONLINE;
    if (anyIdle) return PresenceState.AFK;
    return PresenceState.OFFLINE;
  }
}
```

- [ ] **Step 1.6: Implement `RedisPresenceService` changes**

Replace `backend/src/main/java/com/hackathon/features/presence/RedisPresenceService.java`:

```java
package com.hackathon.features.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.features.sessions.SessionView;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class RedisPresenceService implements PresenceService {
  private static final String KEY_PREFIX = "presence:";
  private static final String SCAN_PATTERN = "presence:*";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  @Getter private String instanceId;

  @PostConstruct
  void init() {
    instanceId = UUID.randomUUID().toString();
    log.info("Presence instance id = {}", instanceId);
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }

  private record SessionEntry(
      boolean idle,
      long lastSeen,
      long connectedAt,
      String instance,
      String userAgent,
      String remoteAddr,
      String tokenHash) {}

  private String toJson(SessionEntry e) {
    try {
      return objectMapper.writeValueAsString(e);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private SessionEntry fromJson(String s) {
    try {
      return objectMapper.readValue(s, SessionEntry.class);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void markOnline(
      UUID userId, String sessionId, String userAgent, String remoteAddr, String tokenHash) {
    long now = System.currentTimeMillis();
    SessionEntry entry =
        new SessionEntry(false, now, now, instanceId, userAgent, remoteAddr, tokenHash);
    redis.opsForHash().put(key(userId), sessionId, toJson(entry));
  }

  @Override
  public void markAfk(UUID userId, String sessionId) {
    Object raw = redis.opsForHash().get(key(userId), sessionId);
    if (raw == null) return;
    SessionEntry existing = fromJson(raw.toString());
    SessionEntry updated =
        new SessionEntry(
            true,
            System.currentTimeMillis(),
            existing.connectedAt(),
            existing.instance(),
            existing.userAgent(),
            existing.remoteAddr(),
            existing.tokenHash());
    redis.opsForHash().put(key(userId), sessionId, toJson(updated));
  }

  @Override
  public void markActive(UUID userId, String sessionId) {
    Object raw = redis.opsForHash().get(key(userId), sessionId);
    if (raw == null) {
      markOnline(userId, sessionId, null, null, null);
      return;
    }
    SessionEntry existing = fromJson(raw.toString());
    SessionEntry updated =
        new SessionEntry(
            false,
            System.currentTimeMillis(),
            existing.connectedAt(),
            existing.instance(),
            existing.userAgent(),
            existing.remoteAddr(),
            existing.tokenHash());
    redis.opsForHash().put(key(userId), sessionId, toJson(updated));
  }

  @Override
  public void heartbeat(UUID userId, String sessionId) {
    Object raw = redis.opsForHash().get(key(userId), sessionId);
    if (raw == null) return;
    SessionEntry existing = fromJson(raw.toString());
    SessionEntry updated =
        new SessionEntry(
            existing.idle(),
            System.currentTimeMillis(),
            existing.connectedAt(),
            existing.instance(),
            existing.userAgent(),
            existing.remoteAddr(),
            existing.tokenHash());
    redis.opsForHash().put(key(userId), sessionId, toJson(updated));
  }

  @Override
  public void markOffline(UUID userId, String sessionId) {
    redis.opsForHash().delete(key(userId), sessionId);
    Long size = redis.opsForHash().size(key(userId));
    if (size != null && size == 0) redis.delete(key(userId));
  }

  @Override
  public PresenceState aggregate(UUID userId) {
    Map<Object, Object> all = redis.opsForHash().entries(key(userId));
    if (all.isEmpty()) return PresenceState.OFFLINE;
    boolean anyActive = false, anyIdle = false;
    for (Object v : all.values()) {
      SessionEntry e = fromJson(v.toString());
      if (e.idle()) anyIdle = true;
      else anyActive = true;
    }
    if (anyActive) return PresenceState.ONLINE;
    if (anyIdle) return PresenceState.AFK;
    return PresenceState.OFFLINE;
  }

  @Override
  public Map<UUID, PresenceState> snapshot(List<UUID> userIds) {
    Map<UUID, PresenceState> out = new HashMap<>();
    for (UUID id : userIds) out.put(id, aggregate(id));
    return out;
  }

  @Override
  public List<UUID> evictStaleOwnedBy(String instanceId, long cutoffEpochMillis) {
    List<UUID> affected = new ArrayList<>();
    Set<String> keys = redis.keys(SCAN_PATTERN);
    if (keys == null) return affected;
    for (String hashKey : keys) {
      UUID userId;
      try {
        userId = UUID.fromString(hashKey.substring(KEY_PREFIX.length()));
      } catch (IllegalArgumentException e) {
        continue;
      }
      Map<Object, Object> entries = redis.opsForHash().entries(hashKey);
      boolean changed = false;
      for (Map.Entry<Object, Object> entry : entries.entrySet()) {
        SessionEntry e = fromJson(entry.getValue().toString());
        if (e.instance().equals(instanceId) && e.lastSeen() < cutoffEpochMillis) {
          redis.opsForHash().delete(hashKey, entry.getKey());
          changed = true;
        }
      }
      if (changed) {
        Long size = redis.opsForHash().size(hashKey);
        if (size != null && size == 0) redis.delete(hashKey);
        affected.add(userId);
      }
    }
    return affected;
  }

  @Override
  public List<SessionView> listSessions(UUID userId) {
    Map<Object, Object> all = redis.opsForHash().entries(key(userId));
    List<SessionView> out = new ArrayList<>(all.size());
    for (Map.Entry<Object, Object> entry : all.entrySet()) {
      SessionEntry e = fromJson(entry.getValue().toString());
      out.add(
          new SessionView(
              entry.getKey().toString(),
              e.userAgent(),
              e.remoteAddr(),
              Instant.ofEpochMilli(e.connectedAt()),
              Instant.ofEpochMilli(e.lastSeen()),
              e.idle(),
              e.tokenHash()));
    }
    return out;
  }
}
```

- [ ] **Step 1.7: Update `PresenceEventListener` to pass nulls (for now)**

In `backend/src/main/java/com/hackathon/features/presence/PresenceEventListener.java`, change the single `markOnline` call inside `onConnect` from:

```java
        presenceService.markOnline(userId, sessionId);
```

to:

```java
        presenceService.markOnline(userId, sessionId, null, null, null);
```

(Task 2 will replace the nulls with values read from session attributes.)

- [ ] **Step 1.8: Run the full backend test suite**

Run:

```bash
cd backend && ./gradlew compileJava compileTestJava test
```

Expected: PASS. All existing presence tests green. Two new tests pass.

- [ ] **Step 1.9: Commit**

```bash
git add backend/src/main/java/com/hackathon/features/presence/PresenceService.java \
        backend/src/main/java/com/hackathon/features/presence/InMemoryPresenceService.java \
        backend/src/main/java/com/hackathon/features/presence/RedisPresenceService.java \
        backend/src/main/java/com/hackathon/features/presence/PresenceEventListener.java \
        backend/src/main/java/com/hackathon/features/sessions/SessionView.java \
        backend/src/test/java/com/hackathon/features/presence/InMemoryPresenceServiceTest.java
git commit -m "feat(sessions): extend PresenceService with per-session metadata" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Capture UA/IP/token-hash into WS session attributes

**Goal:** When a WS connects, capture `User-Agent`, remote IP, and SHA-256 of the raw JWT into the session attributes. `PresenceEventListener` reads them and forwards into `markOnline`. Keeps the data model in Task 1 populated end-to-end.

**Files:**
- Create: `backend/src/main/java/com/hackathon/shared/security/TokenHashing.java`
- Create: `backend/src/main/java/com/hackathon/shared/websocket/SessionAttrHandshakeInterceptor.java`
- Modify: `backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java`
- Modify: `backend/src/main/java/com/hackathon/features/presence/PresenceEventListener.java`

- [ ] **Step 2.1: Create `TokenHashing` helper**

Create `backend/src/main/java/com/hackathon/shared/security/TokenHashing.java`:

```java
package com.hackathon.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 hex digest of a raw JWT string. Used as the revocation-set key. */
public final class TokenHashing {
  private TokenHashing() {}

  public static String sha256Hex(String token) {
    if (token == null) return null;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
```

- [ ] **Step 2.2: Create the handshake interceptor**

Create `backend/src/main/java/com/hackathon/shared/websocket/SessionAttrHandshakeInterceptor.java`:

```java
package com.hackathon.shared.websocket;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Copies the HTTP User-Agent and remote-address from the initial SockJS handshake
 * into the WS session attributes. STOMP CONNECT frames don't carry the IP and
 * SockJS doesn't surface the User-Agent as a STOMP header, so we grab both here.
 */
@Component
public class SessionAttrHandshakeInterceptor implements HandshakeInterceptor {
  public static final String ATTR_USER_AGENT = "userAgent";
  public static final String ATTR_REMOTE_ADDR = "remoteAddr";

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String userAgent = null;
    var agents = request.getHeaders().get("User-Agent");
    if (agents != null && !agents.isEmpty()) userAgent = agents.get(0);
    attributes.put(ATTR_USER_AGENT, userAgent);

    String remoteAddr = null;
    if (request instanceof ServletServerHttpRequest servletReq) {
      remoteAddr = servletReq.getServletRequest().getRemoteAddr();
    }
    attributes.put(ATTR_REMOTE_ADDR, remoteAddr);
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}
}
```

- [ ] **Step 2.3: Wire the handshake interceptor + capture token hash on CONNECT**

Replace `backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java`:

```java
package com.hackathon.shared.websocket;

import com.hackathon.shared.security.JwtTokenProvider;
import com.hackathon.shared.security.TokenHashing;
import java.security.Principal;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  public static final String ATTR_TOKEN_HASH = "tokenHash";
  private final JwtTokenProvider jwtTokenProvider;
  private final SessionAttrHandshakeInterceptor handshakeInterceptor;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic", "/queue", "/user");
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws/chat")
        .setAllowedOriginPatterns("*")
        .addInterceptors(handshakeInterceptor)
        .withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
              String authHeader = accessor.getFirstNativeHeader("Authorization");
              if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                  var userId = jwtTokenProvider.getUserIdFromToken(token);
                  Principal principal =
                      new UsernamePasswordAuthenticationToken(
                          userId.toString(), null, Collections.emptyList());
                  accessor.setUser(principal);
                  var attrs = accessor.getSessionAttributes();
                  if (attrs != null) {
                    attrs.put(ATTR_TOKEN_HASH, TokenHashing.sha256Hex(token));
                  }
                }
              }
            }
            return message;
          }
        });
  }
}
```

- [ ] **Step 2.4: Update `PresenceEventListener` to read from session attributes**

Replace `backend/src/main/java/com/hackathon/features/presence/PresenceEventListener.java`:

```java
package com.hackathon.features.presence;

import com.hackathon.shared.websocket.SessionAttrHandshakeInterceptor;
import com.hackathon.shared.websocket.WebSocketConfig;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {
    private final PresenceService presenceService;
    private final PresencePublisher presencePublisher;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user == null || sessionId == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            log.warn("SessionConnectedEvent with non-UUID principal: {}", user.getName());
            return;
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String userAgent = null, remoteAddr = null, tokenHash = null;
        if (attrs != null) {
            Object ua = attrs.get(SessionAttrHandshakeInterceptor.ATTR_USER_AGENT);
            Object ip = attrs.get(SessionAttrHandshakeInterceptor.ATTR_REMOTE_ADDR);
            Object th = attrs.get(WebSocketConfig.ATTR_TOKEN_HASH);
            userAgent = ua == null ? null : ua.toString();
            remoteAddr = ip == null ? null : ip.toString();
            tokenHash = th == null ? null : th.toString();
        }
        presenceService.markOnline(userId, sessionId, userAgent, remoteAddr, tokenHash);
        presencePublisher.publish(userId, presenceService.aggregate(userId));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user == null || sessionId == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            return;
        }
        presenceService.markOffline(userId, sessionId);
        presencePublisher.publish(userId, presenceService.aggregate(userId));
    }
}
```

- [ ] **Step 2.5: Compile + run tests**

Run:

```bash
cd backend && ./gradlew compileJava compileTestJava test
```

Expected: PASS. No new failing tests, existing suites unchanged (capture path is only exercised at runtime with a real handshake; runtime smoke-tested in Task 5).

- [ ] **Step 2.6: Commit**

```bash
git add backend/src/main/java/com/hackathon/shared/security/TokenHashing.java \
        backend/src/main/java/com/hackathon/shared/websocket/SessionAttrHandshakeInterceptor.java \
        backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java \
        backend/src/main/java/com/hackathon/features/presence/PresenceEventListener.java
git commit -m "feat(sessions): capture UA/IP/token-hash on WS handshake" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Token revocation infrastructure + REST filter check

**Goal:** Introduce `TokenRevocationService` (Redis-backed in prod, no-op in tests), wire `JwtAuthenticationFilter` to reject revoked tokens with 401, and wire the WS CONNECT interceptor to reject revoked tokens. Also expose JWT expiration epoch millis from `JwtTokenProvider` so revocation TTLs can match.

**Files:**
- Create: `backend/src/main/java/com/hackathon/shared/security/TokenRevocationService.java`
- Create: `backend/src/main/java/com/hackathon/shared/security/RedisTokenRevocationService.java`
- Create: `backend/src/main/java/com/hackathon/shared/security/NoopTokenRevocationService.java`
- Create: `backend/src/test/java/com/hackathon/shared/security/JwtAuthenticationFilterTest.java`
- Modify: `backend/src/main/java/com/hackathon/shared/security/JwtTokenProvider.java`
- Modify: `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java`

- [ ] **Step 3.1: Add `TokenRevocationService` interface**

Create `backend/src/main/java/com/hackathon/shared/security/TokenRevocationService.java`:

```java
package com.hackathon.shared.security;

public interface TokenRevocationService {
  /** Mark a token hash as revoked for the given TTL in seconds. Safe to call with ttl <= 0 (no-op). */
  void revoke(String tokenHash, long ttlSeconds);

  /** True iff this token hash was revoked and hasn't yet expired. */
  boolean isRevoked(String tokenHash);
}
```

- [ ] **Step 3.2: Add Redis-backed implementation**

Create `backend/src/main/java/com/hackathon/shared/security/RedisTokenRevocationService.java`:

```java
package com.hackathon.shared.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class RedisTokenRevocationService implements TokenRevocationService {
  private static final String KEY_PREFIX = "revoked_token:";

  private final StringRedisTemplate redis;

  @Override
  public void revoke(String tokenHash, long ttlSeconds) {
    if (tokenHash == null || tokenHash.isEmpty() || ttlSeconds <= 0) return;
    redis.opsForValue().set(KEY_PREFIX + tokenHash, "1", Duration.ofSeconds(ttlSeconds));
  }

  @Override
  public boolean isRevoked(String tokenHash) {
    if (tokenHash == null || tokenHash.isEmpty()) return false;
    Boolean present = redis.hasKey(KEY_PREFIX + tokenHash);
    return present != null && present;
  }
}
```

- [ ] **Step 3.3: Add no-op test implementation**

Create `backend/src/main/java/com/hackathon/shared/security/NoopTokenRevocationService.java`:

```java
package com.hackathon.shared.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoopTokenRevocationService implements TokenRevocationService {
  @Override
  public void revoke(String tokenHash, long ttlSeconds) {}

  @Override
  public boolean isRevoked(String tokenHash) {
    return false;
  }
}
```

- [ ] **Step 3.4: Expose JWT expiration on `JwtTokenProvider`**

In `backend/src/main/java/com/hackathon/shared/security/JwtTokenProvider.java`, add this method:

```java
  /** Returns the JWT's expiration time as epoch millis, or 0 if the token is invalid. */
  public long getExpirationEpochMillis(String token) {
    try {
      SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
      return Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getExpiration()
          .getTime();
    } catch (Exception e) {
      return 0L;
    }
  }
```

- [ ] **Step 3.5: Write failing test for revocation in `JwtAuthenticationFilter`**

Create `backend/src/test/java/com/hackathon/shared/security/JwtAuthenticationFilterTest.java`:

```java
package com.hackathon.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class JwtAuthenticationFilterTest {

  @Autowired private JwtAuthenticationFilter filter;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private UserRepository userRepository;
  @MockitoSpyBean private TokenRevocationService tokenRevocationService;

  @Test
  void revokedTokenDoesNotAuthenticate() throws Exception {
    SecurityContextHolder.clearContext();
    User u = new User();
    u.setId(UUID.randomUUID());
    u.setUsername("revtest-" + System.nanoTime());
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash("x");
    userRepository.save(u);

    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());
    String hash = TokenHashing.sha256Hex(token);
    Mockito.doReturn(true).when(tokenRevocationService).isRevoked(hash);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = (request, response) -> {};

    filter.doFilter(req, resp, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void nonRevokedTokenAuthenticatesNormally() throws Exception {
    SecurityContextHolder.clearContext();
    User u = new User();
    u.setId(UUID.randomUUID());
    u.setUsername("nonrev-" + System.nanoTime());
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash("x");
    userRepository.save(u);

    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());
    // NoopTokenRevocationService returns false by default; no stubbing needed.

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = (request, response) -> {};

    filter.doFilter(req, resp, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }
}
```

- [ ] **Step 3.6: Run the failing test**

Run:

```bash
cd backend && ./gradlew test --tests 'com.hackathon.shared.security.JwtAuthenticationFilterTest'
```

Expected: `revokedTokenDoesNotAuthenticate` FAILS — filter currently ignores revocation; `nonRevokedTokenAuthenticatesNormally` may pass by accident but is kept as regression.

- [ ] **Step 3.7: Add revocation check to `JwtAuthenticationFilter`**

Replace `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java`:

```java
package com.hackathon.shared.security;

import com.hackathon.features.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;
  private final TokenRevocationService tokenRevocationService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (jwtTokenProvider.validateToken(token)
          && !tokenRevocationService.isRevoked(TokenHashing.sha256Hex(token))) {
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userRepository.existsById(userId)) {
          String username = jwtTokenProvider.getUsernameFromToken(token);
          UsernamePasswordAuthenticationToken auth =
              new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
          auth.setDetails(userId);
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    }
    chain.doFilter(request, response);
  }
}
```

- [ ] **Step 3.8: Add revocation check to the WS CONNECT interceptor**

In `backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java`, inject `TokenRevocationService` and check inside the CONNECT branch. Replace the file:

```java
package com.hackathon.shared.websocket;

import com.hackathon.shared.security.JwtTokenProvider;
import com.hackathon.shared.security.TokenHashing;
import com.hackathon.shared.security.TokenRevocationService;
import java.security.Principal;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  public static final String ATTR_TOKEN_HASH = "tokenHash";
  private final JwtTokenProvider jwtTokenProvider;
  private final SessionAttrHandshakeInterceptor handshakeInterceptor;
  private final TokenRevocationService tokenRevocationService;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic", "/queue", "/user");
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws/chat")
        .setAllowedOriginPatterns("*")
        .addInterceptors(handshakeInterceptor)
        .withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
              String authHeader = accessor.getFirstNativeHeader("Authorization");
              if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String hash = TokenHashing.sha256Hex(token);
                if (!jwtTokenProvider.validateToken(token)
                    || tokenRevocationService.isRevoked(hash)) {
                  throw new MessageDeliveryException("Unauthorized CONNECT");
                }
                var userId = jwtTokenProvider.getUserIdFromToken(token);
                Principal principal =
                    new UsernamePasswordAuthenticationToken(
                        userId.toString(), null, Collections.emptyList());
                accessor.setUser(principal);
                var attrs = accessor.getSessionAttributes();
                if (attrs != null) attrs.put(ATTR_TOKEN_HASH, hash);
              }
            }
            return message;
          }
        });
  }
}
```

- [ ] **Step 3.9: Run tests**

Run:

```bash
cd backend && ./gradlew compileJava compileTestJava test
```

Expected: PASS. Revocation tests green; existing suites unchanged.

- [ ] **Step 3.10: Commit**

```bash
git add backend/src/main/java/com/hackathon/shared/security/TokenRevocationService.java \
        backend/src/main/java/com/hackathon/shared/security/RedisTokenRevocationService.java \
        backend/src/main/java/com/hackathon/shared/security/NoopTokenRevocationService.java \
        backend/src/main/java/com/hackathon/shared/security/JwtTokenProvider.java \
        backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java \
        backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java \
        backend/src/test/java/com/hackathon/shared/security/JwtAuthenticationFilterTest.java
git commit -m "feat(sessions): token revocation service + REST+WS enforcement" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Sessions REST API — list, logout, logout-others

**Goal:** Users hit `GET /api/sessions`, `DELETE /api/sessions/{id}`, `DELETE /api/sessions/others`. Logout: revoke + push `EVICTED` to `/user/{uuid}/queue/sessions` + server-disconnect + markOffline.

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/sessions/SessionDisconnector.java`
- Create: `backend/src/main/java/com/hackathon/features/sessions/SessionService.java`
- Create: `backend/src/main/java/com/hackathon/features/sessions/SessionController.java`
- Create: `backend/src/test/java/com/hackathon/features/sessions/SessionServiceTest.java`
- Create: `backend/src/test/java/com/hackathon/features/sessions/SessionControllerTest.java`
- Create: `backend/src/test/java/com/hackathon/features/sessions/SessionDisconnectorTest.java`

- [ ] **Step 4.1: Write failing test for `SessionDisconnector`**

Create `backend/src/test/java/com/hackathon/features/sessions/SessionDisconnectorTest.java`:

```java
package com.hackathon.features.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

class SessionDisconnectorTest {

  @Test
  void disconnectSendsStompDisconnectForSessionId() {
    MessageChannel channel = mock(MessageChannel.class);
    when(channel.send(any())).thenReturn(true);
    SessionDisconnector disconnector = new SessionDisconnector(channel);

    disconnector.disconnect("s-abc");

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(channel).send(captor.capture());
    StompHeaderAccessor acc = StompHeaderAccessor.wrap(captor.getValue());
    assertThat(acc.getCommand()).isEqualTo(StompCommand.DISCONNECT);
    assertThat(acc.getSessionId()).isEqualTo("s-abc");
  }
}
```

- [ ] **Step 4.2: Run the failing test**

Run:

```bash
cd backend && ./gradlew test --tests 'com.hackathon.features.sessions.SessionDisconnectorTest'
```

Expected: FAIL to compile — `SessionDisconnector` doesn't exist yet.

- [ ] **Step 4.3: Implement `SessionDisconnector`**

Create `backend/src/main/java/com/hackathon/features/sessions/SessionDisconnector.java`:

```java
package com.hackathon.features.sessions;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Forces a STOMP session closed by pushing a DISCONNECT frame onto the outbound
 * channel. Safe to call with a session id that no longer exists — Spring treats
 * it as a no-op.
 */
@Component
public class SessionDisconnector {
  private final MessageChannel clientOutboundChannel;

  public SessionDisconnector(
      @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
    this.clientOutboundChannel = clientOutboundChannel;
  }

  public void disconnect(String sessionId) {
    StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.DISCONNECT);
    acc.setSessionId(sessionId);
    acc.setLeaveMutable(true);
    clientOutboundChannel.send(
        MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders()));
  }
}
```

- [ ] **Step 4.4: Run disconnector test — expect pass**

Run:

```bash
cd backend && ./gradlew test --tests 'com.hackathon.features.sessions.SessionDisconnectorTest'
```

Expected: PASS.

- [ ] **Step 4.5: Implement `SessionService`**

Create `backend/src/main/java/com/hackathon/features/sessions/SessionService.java`:

```java
package com.hackathon.features.sessions;

import com.hackathon.features.presence.PresenceService;
import com.hackathon.shared.security.TokenRevocationService;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {
  private final PresenceService presenceService;
  private final TokenRevocationService tokenRevocationService;
  private final SessionDisconnector sessionDisconnector;
  private final SimpMessagingTemplate messagingTemplate;

  public List<SessionView> list(UUID userId) {
    return presenceService.listSessions(userId);
  }

  /** Logs out one session. Throws NoSuchElementException if the session isn't the user's. */
  public void logout(UUID userId, String sessionId) {
    SessionView target =
        presenceService.listSessions(userId).stream()
            .filter(s -> s.sessionId().equals(sessionId))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("session not found"));
    revokeAndDisconnect(userId, target);
  }

  /** Logs out every session except `currentSessionId`. Returns count revoked. */
  public int logoutOthers(UUID userId, String currentSessionId) {
    List<SessionView> all = presenceService.listSessions(userId);
    List<SessionView> others = new ArrayList<>();
    for (SessionView s : all) if (!s.sessionId().equals(currentSessionId)) others.add(s);
    for (SessionView s : others) revokeAndDisconnect(userId, s);
    return others.size();
  }

  private void revokeAndDisconnect(UUID userId, SessionView s) {
    // Revocation first — even if disconnect is slow, the token can't be re-used.
    if (s.tokenHash() != null && !s.tokenHash().isEmpty()) {
      // TTL = 24h ceiling; tokens expire sooner anyway. Short enough that the set stays bounded.
      tokenRevocationService.revoke(s.tokenHash(), 24 * 60 * 60L);
    }
    // Cooperative notification to the tab: "you were logged out from another device".
    messagingTemplate.convertAndSendToUser(
        userId.toString(),
        "/queue/sessions",
        new EvictedEvent("EVICTED", s.sessionId()));
    // Hard server-side disconnect.
    sessionDisconnector.disconnect(s.sessionId());
    // Remove from presence so the list updates.
    presenceService.markOffline(userId, s.sessionId());
  }

  public record EvictedEvent(String type, String sessionId) {}
}
```

- [ ] **Step 4.6: Implement `SessionController`**

Create `backend/src/main/java/com/hackathon/features/sessions/SessionController.java`:

```java
package com.hackathon.features.sessions;

import com.hackathon.features.users.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {
  private final SessionService sessionService;
  private final UserService userService;

  record SessionRow(
      String sessionId,
      String userAgent,
      String remoteAddr,
      Instant connectedAt,
      Instant lastSeen,
      boolean idle,
      boolean current) {}

  record LogoutOthersResponse(int revokedCount) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping
  public ResponseEntity<List<SessionRow>> list(
      Authentication authentication,
      @RequestHeader(name = "X-Session-Id", required = false) String currentSessionId) {
    UUID me = currentUserId(authentication);
    List<SessionView> rows = sessionService.list(me);
    List<SessionRow> out = new ArrayList<>(rows.size());
    for (SessionView v : rows) {
      out.add(
          new SessionRow(
              v.sessionId(),
              v.userAgent(),
              v.remoteAddr(),
              v.connectedAt(),
              v.lastSeen(),
              v.idle(),
              v.sessionId().equals(currentSessionId)));
    }
    return ResponseEntity.ok(out);
  }

  @DeleteMapping("/{sessionId}")
  public ResponseEntity<Void> logout(
      Authentication authentication, @PathVariable String sessionId) {
    UUID me = currentUserId(authentication);
    try {
      sessionService.logout(me, sessionId);
    } catch (NoSuchElementException e) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/others")
  public ResponseEntity<LogoutOthersResponse> logoutOthers(
      Authentication authentication,
      @RequestHeader(name = "X-Session-Id", required = false) String currentSessionId) {
    if (currentSessionId == null || currentSessionId.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    UUID me = currentUserId(authentication);
    int count = sessionService.logoutOthers(me, currentSessionId);
    return ResponseEntity.ok(new LogoutOthersResponse(count));
  }
}
```

- [ ] **Step 4.7: Expose `/api/sessions` in SecurityConfig if needed**

Check: `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java`. `/api/sessions/**` should require authentication. If the config uses an allow-list approach where authenticated paths are the default, no change needed. If it uses an explicit permit-list plus `anyRequest().authenticated()`, `/api/sessions/**` is automatically authenticated — still no change needed. Only edit this file if presence/DM endpoints were special-cased.

Grep to confirm pattern:

```bash
```

Use the Grep tool on `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java` for `authenticated()`. If the config looks like `.anyRequest().authenticated()` then move on without edits. Otherwise, add `.requestMatchers("/api/sessions/**").authenticated()` alongside other authenticated routes.

- [ ] **Step 4.8: Write `SessionServiceTest`**

Create `backend/src/test/java/com/hackathon/features/sessions/SessionServiceTest.java`:

```java
package com.hackathon.features.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hackathon.features.presence.PresenceService;
import com.hackathon.shared.security.TokenRevocationService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class SessionServiceTest {

  @Autowired private SessionService sessionService;
  @Autowired private PresenceService presenceService;
  @MockitoSpyBean private TokenRevocationService tokenRevocationService;
  @MockitoBean private SessionDisconnector sessionDisconnector;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;

  @Test
  void listReturnsAllMySessions() {
    UUID me = UUID.randomUUID();
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(me, "s2", "UA2", "2.2.2.2", "h2");

    List<SessionView> rows = sessionService.list(me);

    assertThat(rows).extracting(SessionView::sessionId).containsExactlyInAnyOrder("s1", "s2");
  }

  @Test
  void logoutOfUnknownSessionThrows() {
    UUID me = UUID.randomUUID();
    assertThatThrownBy(() -> sessionService.logout(me, "ghost"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void logoutRevokesTokenDisconnectsAndRemovesFromPresence() {
    UUID me = UUID.randomUUID();
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");

    sessionService.logout(me, "s1");

    verify(tokenRevocationService).revoke(eq("h1"), anyLong());
    verify(sessionDisconnector).disconnect("s1");
    verify(messagingTemplate).convertAndSendToUser(eq(me.toString()), eq("/queue/sessions"), any());
    assertThat(sessionService.list(me)).isEmpty();
  }

  @Test
  void logoutOthersKeepsCurrentSessionAndReturnsCount() {
    UUID me = UUID.randomUUID();
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(me, "s2", "UA2", "2.2.2.2", "h2");
    presenceService.markOnline(me, "s3", "UA3", "3.3.3.3", "h3");

    int count = sessionService.logoutOthers(me, "s2");

    assertThat(count).isEqualTo(2);
    verify(sessionDisconnector).disconnect("s1");
    verify(sessionDisconnector).disconnect("s3");
    verify(sessionDisconnector, never()).disconnect("s2");
    assertThat(sessionService.list(me)).extracting(SessionView::sessionId).containsExactly("s2");
  }
}
```

- [ ] **Step 4.9: Write `SessionControllerTest`**

Create `backend/src/test/java/com/hackathon/features/sessions/SessionControllerTest.java`:

```java
package com.hackathon.features.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.features.presence.PresenceService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import com.hackathon.shared.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionControllerTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private PresenceService presenceService;
  @MockitoBean private SessionDisconnector sessionDisconnector;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tokenFor(String suffix, UUID id) {
    User u = new User();
    u.setId(id);
    u.setUsername("user-" + suffix);
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash("x");
    userRepository.save(u);
    return jwtTokenProvider.generateToken(u.getId(), u.getUsername());
  }

  private HttpHeaders bearer(String token, String sessionId) {
    HttpHeaders h = new HttpHeaders();
    h.add("Authorization", "Bearer " + token);
    if (sessionId != null) h.add("X-Session-Id", sessionId);
    return h;
  }

  @Test
  void listUnauthenticatedIs401() {
    ResponseEntity<String> resp = restTemplate.getForEntity("/api/sessions", String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void listReturnsSessionsWithCurrentFlag() throws Exception {
    UUID me = UUID.randomUUID();
    String token = tokenFor("list-" + System.nanoTime(), me);
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(me, "s2", "UA2", "2.2.2.2", "h2");

    ResponseEntity<String> resp =
        restTemplate.exchange(
            "/api/sessions",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token, "s2")),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode arr = objectMapper.readTree(resp.getBody());
    boolean s1Current = false, s2Current = false;
    for (JsonNode n : arr) {
      if ("s1".equals(n.get("sessionId").asText())) s1Current = n.get("current").asBoolean();
      if ("s2".equals(n.get("sessionId").asText())) s2Current = n.get("current").asBoolean();
    }
    assertThat(s1Current).isFalse();
    assertThat(s2Current).isTrue();
  }

  @Test
  void logoutOwnSessionReturns204() {
    UUID me = UUID.randomUUID();
    String token = tokenFor("del-" + System.nanoTime(), me);
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");

    ResponseEntity<String> resp =
        restTemplate.exchange(
            "/api/sessions/s1",
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token, null)),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void logoutUnknownSessionReturns404() {
    UUID me = UUID.randomUUID();
    String token = tokenFor("404-" + System.nanoTime(), me);

    ResponseEntity<String> resp =
        restTemplate.exchange(
            "/api/sessions/ghost",
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token, null)),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void logoutOthersWithoutHeaderReturns400() {
    UUID me = UUID.randomUUID();
    String token = tokenFor("400-" + System.nanoTime(), me);

    ResponseEntity<String> resp =
        restTemplate.exchange(
            "/api/sessions/others",
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token, null)),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void logoutOthersRevokesSiblingsAndReturnsCount() throws Exception {
    UUID me = UUID.randomUUID();
    String token = tokenFor("others-" + System.nanoTime(), me);
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(me, "s2", "UA2", "2.2.2.2", "h2");
    presenceService.markOnline(me, "s3", "UA3", "3.3.3.3", "h3");

    ResponseEntity<String> resp =
        restTemplate.exchange(
            "/api/sessions/others",
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(token, "s2")),
            String.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(resp.getBody());
    assertThat(body.get("revokedCount").asInt()).isEqualTo(2);
  }
}
```

- [ ] **Step 4.9.a: Add `ObjectMapper` import to controller test if missing**

No additional action — already imported above.

- [ ] **Step 4.10: Run backend suite**

Run:

```bash
cd backend && ./gradlew compileJava compileTestJava test
```

Expected: PASS. New tests green; existing suites untouched.

- [ ] **Step 4.11: Commit**

```bash
git add backend/src/main/java/com/hackathon/features/sessions/SessionDisconnector.java \
        backend/src/main/java/com/hackathon/features/sessions/SessionService.java \
        backend/src/main/java/com/hackathon/features/sessions/SessionController.java \
        backend/src/test/java/com/hackathon/features/sessions/SessionServiceTest.java \
        backend/src/test/java/com/hackathon/features/sessions/SessionControllerTest.java \
        backend/src/test/java/com/hackathon/features/sessions/SessionDisconnectorTest.java
git commit -m "feat(sessions): REST API for list/logout/logout-others" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Frontend — real sessions page, session-id plumbing, eviction watcher

**Goal:** Replace `SessionsStub` with a live page; capture the STOMP session id; send `X-Session-Id` header on every request; subscribe to `/user/{uuid}/queue/sessions` to detect eviction; mark Feature #12 complete in the roadmap.

**Files:**
- Create: `frontend/src/types/session.ts`
- Create: `frontend/src/services/sessionsService.ts`
- Create: `frontend/src/hooks/useEvictedSessionWatcher.ts`
- Create: `frontend/src/pages/SessionsPage.tsx`
- Modify: `frontend/src/services/websocketService.ts`
- Modify: `frontend/src/services/authService.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/layout/AppShell.tsx`
- Delete: `frontend/src/pages/SessionsStub.tsx`
- Modify: `FEATURES_ROADMAP.md`

- [ ] **Step 5.1: Add session types**

Create `frontend/src/types/session.ts`:

```ts
export interface SessionRow {
  sessionId: string;
  userAgent: string | null;
  remoteAddr: string | null;
  connectedAt: string;
  lastSeen: string;
  idle: boolean;
  current: boolean;
}

export interface LogoutOthersResponse {
  revokedCount: number;
}
```

- [ ] **Step 5.2: Capture STOMP session id in `websocketService`**

Modify `frontend/src/services/websocketService.ts`. In the `connect` method, capture the session id from the CONNECT frame callback and expose it. Replace the class body:

```ts
class WebSocketService {
  private client: Stomp.Client | null = null;
  private pendingConnection: Promise<void> | null = null;
  private subscriptions: Map<string, Subscription> = new Map();
  private sessionId: string | null = null;

  connect(token?: string): Promise<void> {
    if (this.client && this.client.connected) return Promise.resolve();
    if (this.pendingConnection) return this.pendingConnection;

    this.pendingConnection = new Promise<void>((resolve, reject) => {
      const socket = new SockJS('/ws/chat');
      const client = Stomp.over(socket);
      client.debug = () => {};
      this.client = client;

      client.connect(
        {
          ...(token && { Authorization: `Bearer ${token}` }),
        },
        (frame?: { headers?: Record<string, string> }) => {
          this.pendingConnection = null;
          this.sessionId = frame?.headers?.session ?? null;
          resolve();
        },
        (error: unknown) => {
          this.pendingConnection = null;
          this.sessionId = null;
          if (this.client === client) this.client = null;
          reject(new Error(`WebSocket connection failed: ${String(error)}`));
        },
      );
    });

    return this.pendingConnection;
  }

  disconnect(): void {
    const client = this.client;
    if (!client) return;

    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();

    if (client.connected) {
      client.disconnect(() => {
        if (this.client === client) this.client = null;
        this.sessionId = null;
      });
    } else {
      if (this.client === client) this.client = null;
      this.sessionId = null;
    }
  }

  subscribe(
    destination: string,
    callback: (message: WebSocketMessage) => void,
  ): Subscription {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }
    const existing = this.subscriptions.get(destination);
    if (existing) existing.unsubscribe();

    const raw = this.client.subscribe(destination, (frame: { body: string }) => {
      try {
        const body = JSON.parse(frame.body) as WebSocketMessage;
        callback(body);
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
        callback({ raw: frame.body });
      }
    });

    const wrapped: Subscription = {
      unsubscribe: () => {
        raw.unsubscribe();
        if (this.subscriptions.get(destination) === wrapped) {
          this.subscriptions.delete(destination);
        }
      },
    };

    this.subscriptions.set(destination, wrapped);
    return wrapped;
  }

  send(destination: string, body: WebSocketMessage): void {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }
    this.client.send(destination, {}, JSON.stringify(body));
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  getSessionId(): string | null {
    return this.sessionId;
  }
}
```

(Leave the imports and the `export const websocketService = new WebSocketService();` line unchanged.)

- [ ] **Step 5.3: Add `X-Session-Id` request interceptor**

In `frontend/src/services/authService.ts`, extend the existing request interceptor. Replace the block (lines 9–15) with:

```ts
// Attach Bearer token + STOMP session id to every outgoing request.
// Without the Bearer interceptor, full-page reloads lose the in-memory axios.defaults
// header set by setAuthToken at login time, and every authenticated request gets 403.
import { websocketService } from './websocketService';

axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token && config.headers) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  const sid = websocketService.getSessionId();
  if (sid && config.headers) {
    config.headers['X-Session-Id'] = sid;
  }
  return config;
});
```

(Move the `import { websocketService }` to the top of the file alongside the other imports.)

- [ ] **Step 5.4: Implement `sessionsService`**

Create `frontend/src/services/sessionsService.ts`:

```ts
import axios from 'axios';
import type { SessionRow, LogoutOthersResponse } from '../types/session';

export const sessionsService = {
  async list(): Promise<SessionRow[]> {
    return (await axios.get<SessionRow[]>('/api/sessions')).data;
  },
  async logout(sessionId: string): Promise<void> {
    await axios.delete(`/api/sessions/${encodeURIComponent(sessionId)}`);
  },
  async logoutOthers(): Promise<LogoutOthersResponse> {
    return (await axios.delete<LogoutOthersResponse>('/api/sessions/others')).data;
  },
};
```

- [ ] **Step 5.5: Implement eviction watcher hook**

Create `frontend/src/hooks/useEvictedSessionWatcher.ts`:

```ts
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { websocketService } from '../services/websocketService';
import { authService } from '../services/authService';

function currentUserId(): string | null {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
}

/**
 * Subscribes once per logged-in session to /user/{uuid}/queue/sessions. When the
 * server evicts *this* session (sessionId matches the live STOMP session id), clear
 * the auth token and redirect to /login. Evictions of sibling sessions are no-ops
 * here — the Sessions page polls and will pick them up on its next tick.
 */
export function useEvictedSessionWatcher(): void {
  const navigate = useNavigate();
  useEffect(() => {
    let sub: { unsubscribe: () => void } | null = null;

    const install = () => {
      if (!websocketService.isConnected()) return;
      const userId = currentUserId();
      if (!userId) return;
      try {
        sub = websocketService.subscribe(
          `/user/${userId}/queue/sessions`,
          (msg) => {
            const payload = msg as { type?: string; sessionId?: string };
            if (payload.type !== 'EVICTED') return;
            if (payload.sessionId && payload.sessionId === websocketService.getSessionId()) {
              authService.removeAuthToken();
              websocketService.disconnect();
              navigate('/login', { replace: true });
            }
          },
        );
      } catch {
        // WS not ready; retry on next mount / connection.
      }
    };

    install();
    const intervalId = window.setInterval(() => {
      if (!sub) install();
    }, 2000);

    return () => {
      window.clearInterval(intervalId);
      if (sub) sub.unsubscribe();
    };
  }, [navigate]);
}
```

- [ ] **Step 5.6: Wire watcher in `AppShell`**

Modify `frontend/src/layout/AppShell.tsx`. Add the import + hook call:

```tsx
import React from 'react';
import { Outlet } from 'react-router-dom';
import { TopMenu } from './TopMenu';
import { RightPanel } from './RightPanel';
import { SideTree } from './SideTree';
import { useAfkTracking } from '../hooks/useAfkTracking';
import { useEvictedSessionWatcher } from '../hooks/useEvictedSessionWatcher';

const getUsername = (): string => {
  const token = localStorage.getItem('authToken');
  if (!token) return 'User';
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.username === 'string' ? payload.username : 'User';
  } catch {
    return 'User';
  }
};

export const AppShell: React.FC = () => {
  useAfkTracking();
  useEvictedSessionWatcher();
  const username = getUsername();
  return (
    <div className="flex flex-col h-screen">
      <TopMenu username={username} />
      <div className="flex flex-1 min-h-0">
        <aside className="w-64 border-r bg-white overflow-y-auto" aria-label="Workspace">
          <SideTree />
        </aside>
        <main className="flex-1 min-w-0 overflow-hidden">
          <Outlet />
        </main>
        <RightPanel />
      </div>
    </div>
  );
};
```

- [ ] **Step 5.7: Implement `SessionsPage`**

Create `frontend/src/pages/SessionsPage.tsx`:

```tsx
import React, { useCallback, useEffect, useState } from 'react';
import { sessionsService } from '../services/sessionsService';
import type { SessionRow } from '../types/session';

const POLL_MS = 30_000;

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export const SessionsPage: React.FC = () => {
  const [rows, setRows] = useState<SessionRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await sessionsService.list();
      setRows(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    load();
    const id = window.setInterval(load, POLL_MS);
    return () => window.clearInterval(id);
  }, [load]);

  const onLogout = async (sessionId: string) => {
    if (!window.confirm('Log out this session?')) return;
    setBusy(true);
    try {
      await sessionsService.logout(sessionId);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const onLogoutOthers = async () => {
    if (!window.confirm('Log out every other device?')) return;
    setBusy(true);
    try {
      await sessionsService.logoutOthers();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const otherCount = rows ? rows.filter((r) => !r.current).length : 0;

  return (
    <div className="p-8 max-w-3xl">
      <div className="flex justify-between items-start mb-4">
        <div>
          <h1 className="text-2xl font-bold">Active sessions</h1>
          <p className="text-sm text-gray-500">WebSocket sessions currently tied to your account.</p>
        </div>
        <button
          onClick={onLogoutOthers}
          disabled={busy || otherCount === 0}
          className="px-3 py-2 border rounded text-sm disabled:opacity-50 hover:bg-red-50"
        >
          Log out everywhere else
        </button>
      </div>

      {error && <div className="mb-3 text-sm text-red-600">{error}</div>}

      {rows === null ? (
        <div className="text-sm text-gray-500">Loading…</div>
      ) : rows.length === 0 ? (
        <div className="text-sm text-gray-500">No active sessions found.</div>
      ) : (
        <table className="w-full border-t text-sm">
          <thead>
            <tr className="text-left text-xs uppercase text-gray-500">
              <th className="py-2">Device</th>
              <th className="py-2">IP</th>
              <th className="py-2">Connected</th>
              <th className="py-2">Last seen</th>
              <th className="py-2"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.sessionId} className="border-t align-top">
                <td className="py-2 pr-2 max-w-xs truncate" title={r.userAgent ?? ''}>
                  {r.userAgent ?? 'Unknown'}
                </td>
                <td className="py-2 pr-2">{r.remoteAddr ?? '—'}</td>
                <td className="py-2 pr-2">{formatDate(r.connectedAt)}</td>
                <td className="py-2 pr-2">{formatDate(r.lastSeen)}</td>
                <td className="py-2 text-right">
                  {r.current ? (
                    <span className="text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded">This device</span>
                  ) : (
                    <button
                      onClick={() => onLogout(r.sessionId)}
                      disabled={busy}
                      className="text-xs px-2 py-1 border rounded hover:bg-red-50 disabled:opacity-50"
                    >
                      Log out
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};
```

- [ ] **Step 5.8: Swap stub for real page in `App.tsx`**

Modify `frontend/src/App.tsx`. Replace the import and route:

```tsx
import { SessionsPage } from './pages/SessionsPage';
```

and change the route:

```tsx
            <Route path="/sessions" element={<SessionsPage />} />
```

- [ ] **Step 5.9: Delete the stub**

Run:

```bash
rm frontend/src/pages/SessionsStub.tsx
```

- [ ] **Step 5.10: Build the frontend**

Run:

```bash
cd frontend && npm run build
```

Expected: build succeeds with no TypeScript errors.

- [ ] **Step 5.11: Update roadmap**

In `FEATURES_ROADMAP.md`, find the Feature #12 (Sessions Management) row and mark it as completed with date 2026-04-19. Follow the pattern used for Feature #7's completion.

- [ ] **Step 5.12: Smoke-test in Docker**

Run:

```bash
docker compose up -d --build backend frontend
```

In a browser:
1. Open the app in two tabs, both logged in as the same user.
2. Go to `/sessions` — both tabs should appear, one badged "This device".
3. Click "Log out" on the other tab's row — the other tab should be redirected to `/login` with the "You were logged out" path; the current tab's list refreshes to show one row.
4. Log in again on that second tab, then click "Log out everywhere else" from tab A — tab B disconnects and is redirected to `/login`.

If the smoke test reveals issues, fix them inline before committing (particularly around WS subscription timing — the 2s retry interval in the watcher is intentional for the race where AppShell mounts before the WS is connected).

- [ ] **Step 5.13: Commit**

```bash
git add frontend/src/types/session.ts \
        frontend/src/services/sessionsService.ts \
        frontend/src/services/websocketService.ts \
        frontend/src/services/authService.ts \
        frontend/src/hooks/useEvictedSessionWatcher.ts \
        frontend/src/pages/SessionsPage.tsx \
        frontend/src/App.tsx \
        frontend/src/layout/AppShell.tsx \
        FEATURES_ROADMAP.md
git rm frontend/src/pages/SessionsStub.tsx
git commit -m "feat(sessions): live /sessions page + eviction watcher + roadmap" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Notes

- **Spec coverage:** Every spec section maps to tasks 1–5. Data model (§2) ↔ Task 1. CONNECT capture (§4) ↔ Task 2. Revocation + filter (§2/§4) ↔ Task 3. REST API (§3) + SessionDisconnector (§4) ↔ Task 4. Frontend (§5) ↔ Task 5. Testing (§6) embedded in tasks 1/3/4.
- **Green commits:** Each task compiles standalone. Task 1 uses nulls in `PresenceEventListener` so the event flow keeps working before Task 2 wires capture. Task 3's filter change is tolerated by the test profile's no-op revocation service. Task 4's new endpoints don't require other changes.
- **Placeholder scan:** No TBDs, no "add appropriate error handling", no "similar to Task N", no undefined types. `SessionRow` / `SessionView` / `SessionEntry` / `EvictedEvent` are all defined where first used.
- **Type consistency:** `listSessions` is the same name in interface, impls, tests. `sessionId` is consistently a `String`. `tokenHash` is hex SHA-256 everywhere. `X-Session-Id` header name matches frontend interceptor.
