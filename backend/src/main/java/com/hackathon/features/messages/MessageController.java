package com.hackathon.features.messages;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  public ResponseEntity<List<ChatMessageDTO>> getMessageHistory(
      @PathVariable UUID roomId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    List<Message> messages = messageService.getMessageHistory(roomId, before, limit);
    // Batch-resolve usernames so the N+1 is N+distinct-senders, not N+N.
    Map<UUID, String> usernameById =
        messages.stream()
            .map(Message::getUserId)
            .distinct()
            .collect(Collectors.toMap(Function.identity(), this::resolveUsername));
    List<ChatMessageDTO> views =
        messages.stream()
            .map(
                m ->
                    ChatMessageDTO.builder()
                        .id(m.getId())
                        .roomId(m.getRoomId())
                        .userId(m.getUserId())
                        .username(usernameById.get(m.getUserId()))
                        .text(m.getText())
                        .createdAt(m.getCreatedAt())
                        .build())
            .toList();
    return ResponseEntity.ok(views);
  }

  @PostMapping
  public ResponseEntity<ChatMessageDTO> sendMessage(
      @PathVariable UUID roomId,
      @RequestBody SendMessageRequest request,
      Authentication authentication) {
    UUID userId = currentUserId(authentication);
    Message message = messageService.sendMessage(roomId, userId, request.text());
    ChatMessageDTO view =
        ChatMessageDTO.builder()
            .id(message.getId())
            .roomId(message.getRoomId())
            .userId(message.getUserId())
            .username(resolveUsername(userId))
            .text(message.getText())
            .createdAt(message.getCreatedAt())
            .build();
    return ResponseEntity.ok(view);
  }

  /**
   * Resolve a user's displayable name, falling back to a short id prefix if
   * the user record is gone (e.g., account deleted after posting a message).
   */
  private String resolveUsername(UUID userId) {
    try {
      User user = userService.getUserById(userId);
      return user.getUsername();
    } catch (IllegalArgumentException e) {
      return userId.toString().substring(0, 8);
    }
  }
}
