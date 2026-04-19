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
