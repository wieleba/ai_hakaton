package com.hackathon.features.unread;

import com.hackathon.features.users.UserService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UnreadController {

  private final UnreadService unreadService;
  private final UserService userService;

  record UnreadCountsResponse(Map<UUID, Long> rooms, Map<UUID, Long> dms) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping("/api/unread-counts")
  public ResponseEntity<UnreadCountsResponse> counts(Authentication authentication) {
    UUID me = currentUserId(authentication);
    UnreadService.UnreadCounts c = unreadService.counts(me);
    return ResponseEntity.ok(new UnreadCountsResponse(c.rooms(), c.dms()));
  }

  @PostMapping("/api/rooms/{roomId}/read")
  public ResponseEntity<Void> markRoomRead(
      Authentication authentication, @PathVariable UUID roomId) {
    unreadService.markRead(currentUserId(authentication), ChatType.ROOM, roomId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/dms/{conversationId}/read")
  public ResponseEntity<Void> markDmRead(
      Authentication authentication, @PathVariable UUID conversationId) {
    unreadService.markRead(currentUserId(authentication), ChatType.DM, conversationId);
    return ResponseEntity.noContent().build();
  }
}
