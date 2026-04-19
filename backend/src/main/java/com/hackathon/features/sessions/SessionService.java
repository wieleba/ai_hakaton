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
    if (s.tokenHash() != null && !s.tokenHash().isEmpty()) {
      tokenRevocationService.revoke(s.tokenHash(), 24 * 60 * 60L);
    }
    messagingTemplate.convertAndSendToUser(
        userId.toString(),
        "/queue/sessions",
        new EvictedEvent("EVICTED", s.sessionId()));
    sessionDisconnector.disconnect(s.sessionId());
    presenceService.markOffline(userId, s.sessionId());
  }

  public record EvictedEvent(String type, String sessionId) {}
}
