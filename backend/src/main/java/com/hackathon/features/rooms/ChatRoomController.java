package com.hackathon.features.rooms;

import com.hackathon.features.users.UserService;
import java.util.List;
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
  private final RoomMemberService roomMemberService;

  record CreateRoomRequest(String name, String description, String visibility) {}

  record RoomMemberView(UUID userId, String username, String role, boolean isOwner) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping
  public ResponseEntity<ChatRoom> createRoom(
      @RequestBody CreateRoomRequest request, Authentication authentication) {
    ChatRoom room =
        chatRoomService.createRoom(
            request.name(),
            request.description(),
            currentUserId(authentication),
            request.visibility());
    return ResponseEntity.ok(room);
  }

  @GetMapping
  public ResponseEntity<Page<ChatRoom>> listPublicRooms(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(chatRoomService.listPublicRooms(page, limit));
  }

  @GetMapping("/mine")
  public ResponseEntity<List<ChatRoom>> listMyRooms(Authentication authentication) {
    return ResponseEntity.ok(chatRoomService.listMyRooms(currentUserId(authentication)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ChatRoom> getRoom(@PathVariable UUID id, Authentication authentication) {
    ChatRoom room = chatRoomService.getRoomById(id);
    // For private rooms, return 404 to non-members to avoid leaking existence.
    if ("private".equals(room.getVisibility())
        && !roomMemberService.isMember(id, currentUserId(authentication))) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(room);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteRoom(@PathVariable UUID id, Authentication authentication) {
    chatRoomService.deleteRoom(id, currentUserId(authentication));
    return ResponseEntity.noContent().build();
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

  @GetMapping("/{id}/members")
  public ResponseEntity<List<RoomMemberView>> listMembers(@PathVariable UUID id) {
    ChatRoom room = chatRoomService.getRoomById(id);
    UUID ownerId = room.getOwnerId();
    List<RoomMemberView> views =
        roomMemberService.listMembersWithRoles(id).stream()
            .map(
                m -> {
                  var user = userService.getUserById(m.getUserId());
                  return new RoomMemberView(
                      m.getUserId(), user.getUsername(), m.getRole(), m.getUserId().equals(ownerId));
                })
            .toList();
    return ResponseEntity.ok(views);
  }
}
