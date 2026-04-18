package com.hackathon.features.rooms;

import com.hackathon.features.users.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {
  private final ChatRoomService chatRoomService;
  private final UserService userService;

  record CreateRoomRequest(String name, String description) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) {
      return uuid;
    }
    // Fallback: look up by username (tests using @WithMockUser hit this path)
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping
  public ResponseEntity<ChatRoom> createRoom(
      @RequestBody CreateRoomRequest request, Authentication authentication) {
    ChatRoom room =
        chatRoomService.createRoom(request.name(), request.description(), currentUserId(authentication));
    return ResponseEntity.ok(room);
  }

  @GetMapping
  public ResponseEntity<Page<ChatRoom>> listPublicRooms(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(chatRoomService.listPublicRooms(page, limit));
  }

  @PostMapping("/{id}/join")
  public ResponseEntity<Void> joinRoom(@PathVariable UUID id, Authentication authentication) {
    chatRoomService.joinRoom(id, currentUserId(authentication));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/leave")
  public ResponseEntity<Void> leaveRoom(@PathVariable UUID id, Authentication authentication) {
    chatRoomService.leaveRoom(id, currentUserId(authentication));
    return ResponseEntity.ok().build();
  }
}
