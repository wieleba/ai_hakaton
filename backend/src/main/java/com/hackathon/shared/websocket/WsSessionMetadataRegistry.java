package com.hackathon.shared.websocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Carries per-STOMP-session metadata between the CONNECT ChannelInterceptor
 * (where session attributes are reachable) and SessionConnectedEvent
 * (where Spring does NOT populate session attributes on the event's
 * StompHeaderAccessor). Without this, PresenceEventListener would always see
 * null UA / IP / tokenHash.
 *
 * Entries are removed on session disconnect to keep the map bounded.
 */
@Component
public class WsSessionMetadataRegistry {
  public record Metadata(String userAgent, String remoteAddr, String tokenHash) {}

  private final ConcurrentMap<String, Metadata> bySessionId = new ConcurrentHashMap<>();

  public void put(String sessionId, Metadata metadata) {
    if (sessionId == null || metadata == null) return;
    bySessionId.put(sessionId, metadata);
  }

  public Metadata get(String sessionId) {
    return sessionId == null ? null : bySessionId.get(sessionId);
  }

  public void remove(String sessionId) {
    if (sessionId != null) bySessionId.remove(sessionId);
  }
}
