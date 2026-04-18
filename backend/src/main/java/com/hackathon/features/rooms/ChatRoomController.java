package com.hackathon.features.rooms;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {
  private final ChatRoomService chatRoomService;

  record CreateRoomRequest(String name, String description) {}

  @PostMapping
  public ResponseEntity<ChatRoom> createRoom(
      @RequestBody CreateRoomRequest request,
      Authentication authentication) {
    UUID userId = UUID.randomUUID();
    ChatRoom room = chatRoomService.createRoom(request.name, request.description, userId);
    return ResponseEntity.ok(room);
  }

  @GetMapping
  public ResponseEntity<Page<ChatRoom>> listPublicRooms(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int limit) {
    Page<ChatRoom> rooms = chatRoomService.listPublicRooms(page, limit);
    return ResponseEntity.ok(rooms);
  }

  @PostMapping("/{id}/join")
  public ResponseEntity<Void> joinRoom(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID userId = UUID.randomUUID();
    chatRoomService.joinRoom(id, userId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/leave")
  public ResponseEntity<Void> leaveRoom(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID userId = UUID.randomUUID();
    chatRoomService.leaveRoom(id, userId);
    return ResponseEntity.ok().build();
  }
}
