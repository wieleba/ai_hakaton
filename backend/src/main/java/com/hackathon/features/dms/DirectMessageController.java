package com.hackathon.features.dms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DirectMessageController {
  private final DirectMessageService directMessageService;
  private final ConversationService conversationService;
  private final UserService userService;

  record SendMessageBody(String text) {}

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
                  return new ConversationView(
                      conv.getId(),
                      otherId,
                      other.getUsername(),
                      last != null ? last.getText() : null,
                      last != null ? last.getCreatedAt() : null);
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
  public ResponseEntity<List<DirectMessage>> getHistory(
      @PathVariable UUID conversationId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(directMessageService.getHistory(conversationId, before, limit));
  }

  @PostMapping("/{conversationId}/messages")
  public ResponseEntity<DirectMessage> sendMessage(
      @PathVariable UUID conversationId,
      @RequestBody SendMessageBody body,
      Authentication authentication) {
    return ResponseEntity.ok(
        directMessageService.send(currentUserId(authentication), conversationId, body.text()));
  }
}
