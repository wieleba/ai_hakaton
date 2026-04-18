package com.hackathon.shared.websocket;

import com.hackathon.features.dms.DirectMessageService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageHandler {
  private final DirectMessageService directMessageService;

  public record DmPayload(String text, UUID replyToId) {}

  @MessageMapping("/dms/{conversationId}/message")
  public void handleDirectMessage(
      @DestinationVariable UUID conversationId, DmPayload payload, Principal principal) {
    UUID senderUserId = UUID.fromString(principal.getName());
    // Service publishes the CREATED envelope on /user/{uuid}/queue/dms for both participants.
    directMessageService.send(senderUserId, conversationId, payload.text(), payload.replyToId());
  }
}
