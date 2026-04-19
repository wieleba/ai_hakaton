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
    int count;
    try {
      count = sessionService.logoutOthers(me, currentSessionId);
    } catch (NoSuchElementException e) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(new LogoutOthersResponse(count));
  }
}
