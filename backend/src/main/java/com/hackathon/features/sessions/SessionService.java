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
  // Matches the default `jwt.expiration` (24h). If the JWT lifetime is ever bumped
  // past this, the revocation entry must be stretched to match or a revoked token
  // could become reusable after the entry expires.
  private static final long REVOCATION_TTL_SECONDS = 24 * 60 * 60L;

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

  /**
   * Logs out every session except `currentSessionId`. Returns count revoked.
   * Throws NoSuchElementException if `currentSessionId` isn't one of the user's sessions —
   * otherwise a stale or forged header would silently nuke every session including the caller's.
   */
  public int logoutOthers(UUID userId, String currentSessionId) {
    List<SessionView> all = presenceService.listSessions(userId);
    boolean currentPresent = false;
    List<SessionView> others = new ArrayList<>();
    for (SessionView s : all) {
      if (s.sessionId().equals(currentSessionId)) currentPresent = true;
      else others.add(s);
    }
    if (!currentPresent) {
      throw new NoSuchElementException("current session not found");
    }
    for (SessionView s : others) revokeAndDisconnect(userId, s);
    return others.size();
  }

  private void revokeAndDisconnect(UUID userId, SessionView s) {
    // Revoke first — if the disconnect is slow or fails, the token still can't be re-used.
    if (s.tokenHash() != null && !s.tokenHash().isEmpty()) {
      tokenRevocationService.revoke(s.tokenHash(), REVOCATION_TTL_SECONDS);
    }
    // Cooperative notification so the evicted tab can show "logged out from another device".
    messagingTemplate.convertAndSendToUser(
        userId.toString(),
        "/queue/sessions",
        new EvictedEvent("EVICTED", s.sessionId()));
    sessionDisconnector.disconnect(s.sessionId());
    presenceService.markOffline(userId, s.sessionId());
  }

  public record EvictedEvent(String type, String sessionId) {}
}
