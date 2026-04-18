package com.hackathon.features.rooms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RoomInvitationController {
  private final RoomInvitationService roomInvitationService;
  private final ChatRoomRepository chatRoomRepository;
  private final UserService userService;

  record InviteRequest(String username) {}

  record InvitationView(
      UUID id,
      UUID roomId,
      String roomName,
      UUID inviterId,
      String inviterUsername,
      OffsetDateTime createdAt) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping("/api/rooms/{roomId}/invitations")
  public ResponseEntity<RoomInvitation> invite(
      @PathVariable UUID roomId,
      @RequestBody InviteRequest body,
      Authentication authentication) {
    RoomInvitation inv =
        roomInvitationService.invite(roomId, currentUserId(authentication), body.username());
    return ResponseEntity.status(HttpStatus.CREATED).body(inv);
  }

  @GetMapping("/api/rooms/{roomId}/invitations")
  public ResponseEntity<List<RoomInvitation>> listRoomOutgoing(
      @PathVariable UUID roomId, Authentication authentication) {
    return ResponseEntity.ok(
        roomInvitationService.listOutgoingForRoom(roomId, currentUserId(authentication)));
  }

  @DeleteMapping("/api/rooms/{roomId}/invitations/{invitationId}")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID roomId,
      @PathVariable UUID invitationId,
      Authentication authentication) {
    roomInvitationService.cancel(invitationId, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/invitations")
  public ResponseEntity<List<InvitationView>> listMyIncoming(Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<InvitationView> views =
        roomInvitationService.listMyIncoming(me).stream()
            .map(
                inv -> {
                  ChatRoom room = chatRoomRepository.findById(inv.getRoomId()).orElse(null);
                  User inviter = userService.getUserById(inv.getInviterId());
                  return new InvitationView(
                      inv.getId(),
                      inv.getRoomId(),
                      room != null ? room.getName() : "(unknown)",
                      inv.getInviterId(),
                      inviter.getUsername(),
                      inv.getCreatedAt());
                })
            .toList();
    return ResponseEntity.ok(views);
  }

  @PostMapping("/api/invitations/{invitationId}/accept")
  public ResponseEntity<Void> accept(
      @PathVariable UUID invitationId, Authentication authentication) {
    roomInvitationService.accept(invitationId, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/invitations/{invitationId}/decline")
  public ResponseEntity<Void> decline(
      @PathVariable UUID invitationId, Authentication authentication) {
    roomInvitationService.decline(invitationId, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }
}
