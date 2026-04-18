package com.hackathon.shared.websocket;

import com.hackathon.features.messages.MessageService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {
  private final MessageService messageService;

  public record RoomSendPayload(String text, UUID replyToId) {}

  @MessageMapping("/rooms/{roomId}/message")
  public void handleMessage(
      RoomSendPayload payload, @DestinationVariable UUID roomId, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    // Service publishes the CREATED envelope on /topic/room/{roomId} itself.
    messageService.sendMessage(roomId, userId, payload.text(), payload.replyToId());
  }
}
