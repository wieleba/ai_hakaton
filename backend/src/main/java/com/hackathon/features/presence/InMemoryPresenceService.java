package com.hackathon.features.presence;

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
    final String instance;

    SessionEntry(boolean idle, long lastSeen, String instance) {
      this.idle = idle;
      this.lastSeen = lastSeen;
      this.instance = instance;
    }
  }

  private final ConcurrentMap<UUID, ConcurrentMap<String, SessionEntry>> byUser =
      new ConcurrentHashMap<>();

  // Test profile uses a fixed instance id so watchdog tests work deterministically.
  @Getter private final String instanceId = "test-instance";

  @Override
  public void markOnline(UUID userId, String sessionId) {
    byUser
        .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
        .compute(sessionId, (k, existing) -> {
          if (existing == null)
            return new SessionEntry(false, System.currentTimeMillis(), instanceId);
          existing.idle = false;
          existing.lastSeen = System.currentTimeMillis();
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
    markOnline(userId, sessionId);
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
