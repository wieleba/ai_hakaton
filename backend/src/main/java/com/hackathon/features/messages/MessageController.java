package com.hackathon.features.messages;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {
  private final MessageService messageService;

  record SendMessageRequest(String text) {}

  @GetMapping
  public ResponseEntity<List<Message>> getMessageHistory(
      @PathVariable UUID roomId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    List<Message> messages = messageService.getMessageHistory(roomId, before, limit);
    return ResponseEntity.ok(messages);
  }

  @PostMapping
  public ResponseEntity<Message> sendMessage(
      @PathVariable UUID roomId,
      @RequestBody SendMessageRequest request,
      Authentication authentication) {
    UUID userId = UUID.randomUUID();
    Message message = messageService.sendMessage(roomId, userId, request.text);
    return ResponseEntity.ok(message);
  }
}
