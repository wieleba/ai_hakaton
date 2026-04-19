package com.hackathon.features.dms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.DirectMessageDTO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DirectMessageController {
  private final DirectMessageService directMessageService;
  private final ConversationService conversationService;
  private final UserService userService;

  record SendMessageBody(String text, UUID replyToId) {}

  record EditMessageBody(String text) {}

  record ConversationView(
      UUID id,
      UUID otherUserId,
      String otherUsername,
      String lastMessage,
      OffsetDateTime lastMessageAt) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping("/conversations")
  public ResponseEntity<List<ConversationView>> listConversations(Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<ConversationView> views =
        directMessageService.listConversations(me).stream()
            .map(
                conv -> {
                  UUID otherId = conversationService.otherParticipant(conv, me);
                  User other = userService.getUserById(otherId);
                  var last = directMessageService.lastMessage(conv.getId()).orElse(null);
                  String lastText = null;
                  OffsetDateTime lastAt = null;
                  if (last != null) {
                    lastText = last.getDeletedAt() == null ? last.getText() : null;
                    lastAt = last.getCreatedAt();
                  }
                  return new ConversationView(
                      conv.getId(), otherId, other.getUsername(), lastText, lastAt);
                })
            .toList();
    return ResponseEntity.ok(views);
  }

  @GetMapping("/with/{otherUserId}")
  public ResponseEntity<DirectConversation> getOrCreate(
      @PathVariable UUID otherUserId, Authentication authentication) {
    return ResponseEntity.ok(
        conversationService.getOrCreate(currentUserId(authentication), otherUserId));
  }

  @GetMapping("/{conversationId}/messages")
  public ResponseEntity<List<DirectMessageDTO>> getHistory(
      @PathVariable UUID conversationId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    List<DirectMessage> messages = directMessageService.getHistory(conversationId, before, limit);
    List<DirectMessageDTO> views = messages.stream().map(directMessageService::toDto).toList();
    return ResponseEntity.ok(views);
  }

  @PostMapping("/{conversationId}/messages")
  public ResponseEntity<DirectMessageDTO> sendMessage(
      @PathVariable UUID conversationId,
      @RequestBody SendMessageBody body,
      Authentication authentication) {
    DirectMessage sent =
        directMessageService.send(
            currentUserId(authentication), conversationId, body.text(), body.replyToId());
    return ResponseEntity.ok(directMessageService.toDto(sent));
  }

  @PostMapping(path = "/{conversationId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DirectMessageDTO> sendMessageMultipart(
      @PathVariable UUID conversationId,
      @RequestParam(value = "text", required = false) String text,
      @RequestParam(value = "replyToId", required = false) UUID replyToId,
      @RequestParam(value = "file", required = false) MultipartFile file,
      Authentication authentication) throws java.io.IOException {
    String filename = file != null ? file.getOriginalFilename() : null;
    String mimeType = file != null ? file.getContentType() : null;
    long size = file != null ? file.getSize() : 0L;
    java.io.InputStream content = file != null ? file.getInputStream() : null;
    try {
      DirectMessage sent = directMessageService.send(
          currentUserId(authentication), conversationId,
          text, replyToId, filename, mimeType, size, content);
      return ResponseEntity.ok(directMessageService.toDto(sent));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PatchMapping("/{conversationId}/messages/{messageId}")
  public ResponseEntity<DirectMessageDTO> editMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID messageId,
      @RequestBody EditMessageBody body,
      Authentication authentication) {
    try {
      DirectMessage edited =
          directMessageService.editMessage(messageId, currentUserId(authentication), body.text());
      return ResponseEntity.ok(directMessageService.toDto(edited));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping("/{conversationId}/messages/{messageId}")
  public ResponseEntity<Void> deleteMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID messageId,
      Authentication authentication) {
    try {
      directMessageService.deleteMessage(messageId, currentUserId(authentication));
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  record ToggleReactionBody(String emoji) {}

  @PostMapping("/{conversationId}/messages/{messageId}/reactions")
  public ResponseEntity<DirectMessageDTO> toggleReaction(
      @PathVariable UUID conversationId,
      @PathVariable UUID messageId,
      @RequestBody ToggleReactionBody body,
      Authentication authentication) {
    try {
      DirectMessageDTO dto =
          directMessageService.toggleReaction(messageId, currentUserId(authentication), body.emoji());
      return ResponseEntity.ok(dto);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }
}
