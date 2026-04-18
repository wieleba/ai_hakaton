package com.hackathon.features.messages;

import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {
  private final MessageService messageService;
  private final UserService userService;

  record SendMessageRequest(String text, UUID replyToId) {}

  record EditMessageRequest(String text) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) {
      return uuid;
    }
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping
  public ResponseEntity<List<ChatMessageDTO>> getMessageHistory(
      @PathVariable UUID roomId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    List<Message> messages = messageService.getMessageHistory(roomId, before, limit);
    List<ChatMessageDTO> views = messages.stream().map(messageService::toDto).toList();
    return ResponseEntity.ok(views);
  }

  @PostMapping
  public ResponseEntity<ChatMessageDTO> sendMessage(
      @PathVariable UUID roomId,
      @RequestBody SendMessageRequest request,
      Authentication authentication) {
    UUID userId = currentUserId(authentication);
    Message message =
        messageService.sendMessage(roomId, userId, request.text(), request.replyToId());
    return ResponseEntity.ok(messageService.toDto(message));
  }

  @PatchMapping("/{messageId}")
  public ResponseEntity<ChatMessageDTO> editMessage(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      @RequestBody EditMessageRequest request,
      Authentication authentication) {
    try {
      UUID userId = currentUserId(authentication);
      Message edited = messageService.editMessage(messageId, userId, request.text());
      return ResponseEntity.ok(messageService.toDto(edited));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  record ToggleReactionBody(String emoji) {}

  @PostMapping("/{messageId}/reactions")
  public ResponseEntity<ChatMessageDTO> toggleReaction(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      @RequestBody ToggleReactionBody body,
      Authentication authentication) {
    try {
      UUID userId = currentUserId(authentication);
      ChatMessageDTO dto = messageService.toggleReaction(messageId, userId, body.emoji());
      return ResponseEntity.ok(dto);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping("/{messageId}")
  public ResponseEntity<Void> deleteMessage(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      Authentication authentication) {
    try {
      UUID userId = currentUserId(authentication);
      messageService.deleteMessage(messageId, userId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }
}
