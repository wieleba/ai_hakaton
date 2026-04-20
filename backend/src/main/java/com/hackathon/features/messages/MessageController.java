package com.hackathon.features.messages;

import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    List<ChatMessageDTO> views = messageService.toDtos(messages, null);
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

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ChatMessageDTO> sendMessageMultipart(
      @PathVariable UUID roomId,
      @RequestParam(value = "text", required = false) String text,
      @RequestParam(value = "replyToId", required = false) UUID replyToId,
      @RequestParam(value = "file", required = false) MultipartFile file,
      Authentication authentication) throws java.io.IOException {
    UUID userId = currentUserId(authentication);
    String filename = file != null ? file.getOriginalFilename() : null;
    String mimeType = file != null ? file.getContentType() : null;
    long size = file != null ? file.getSize() : 0L;
    java.io.InputStream content = file != null ? file.getInputStream() : null;
    try {
      Message message =
          messageService.sendMessage(roomId, userId, text, replyToId, filename, mimeType, size, content);
      return ResponseEntity.ok(messageService.toDto(message));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
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
