package com.hackathon.features.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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

  @Getter
  private String instanceId;

  @PostConstruct
  void init() {
    instanceId = UUID.randomUUID().toString();
    log.info("Presence instance id = {}", instanceId);
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }

  private record SessionEntry(boolean idle, long lastSeen, String instance) {}

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

  private void upsertSession(UUID userId, String sessionId, boolean idle) {
    SessionEntry entry = new SessionEntry(idle, System.currentTimeMillis(), instanceId);
    redis.opsForHash().put(key(userId), sessionId, toJson(entry));
  }

  @Override
  public void markOnline(UUID userId, String sessionId) {
    upsertSession(userId, sessionId, false);
  }

  @Override
  public void markAfk(UUID userId, String sessionId) {
    Object raw = redis.opsForHash().get(key(userId), sessionId);
    if (raw == null) return;
    SessionEntry existing = fromJson(raw.toString());
    SessionEntry updated = new SessionEntry(true, System.currentTimeMillis(), existing.instance());
    redis.opsForHash().put(key(userId), sessionId, toJson(updated));
  }

  @Override
  public void markActive(UUID userId, String sessionId) {
    Object raw = redis.opsForHash().get(key(userId), sessionId);
    if (raw == null) {
      markOnline(userId, sessionId);
      return;
    }
    SessionEntry existing = fromJson(raw.toString());
    SessionEntry updated = new SessionEntry(false, System.currentTimeMillis(), existing.instance());
    redis.opsForHash().put(key(userId), sessionId, toJson(updated));
  }

  @Override
  public void heartbeat(UUID userId, String sessionId) {
    Object raw = redis.opsForHash().get(key(userId), sessionId);
    if (raw == null) return;
    SessionEntry existing = fromJson(raw.toString());
    SessionEntry updated =
        new SessionEntry(existing.idle(), System.currentTimeMillis(), existing.instance());
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
}
