package com.hackathon.features.messages;

import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {
  private final MessageService messageService;
  private final UserService userService;

  record SendMessageRequest(String text) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) {
      return uuid;
    }
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping
  public ResponseEntity<List<Message>> getMessageHistory(
      @PathVariable UUID roomId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(messageService.getMessageHistory(roomId, before, limit));
  }

  @PostMapping
  public ResponseEntity<Message> sendMessage(
      @PathVariable UUID roomId,
      @RequestBody SendMessageRequest request,
      Authentication authentication) {
    Message message =
        messageService.sendMessage(roomId, currentUserId(authentication), request.text());
    return ResponseEntity.ok(message);
  }
}
