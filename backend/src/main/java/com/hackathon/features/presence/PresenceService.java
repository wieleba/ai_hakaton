package com.hackathon.features.presence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PresenceService {
  /** Record a new ACTIVE session for this user. Idempotent per (userId, sessionId). */
  void markOnline(UUID userId, String sessionId);

  /** Flip an existing session to IDLE. No-op if session unknown. */
  void markAfk(UUID userId, String sessionId);

  /** Flip an existing session to ACTIVE. No-op if session unknown (creates it as ACTIVE). */
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
}
