package com.hackathon.shared.websocket;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectConversationRepository;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.users.UserService;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageHandler {
  private final DirectMessageService directMessageService;
  private final DirectConversationRepository directConversationRepository;
  private final ConversationService conversationService;
  private final UserService userService;
  private final SimpMessagingTemplate messagingTemplate;

  public record DmPayload(String text) {}

  public record DmEvent(
      UUID id,
      UUID conversationId,
      UUID senderId,
      String senderUsername,
      String text,
      OffsetDateTime createdAt) {}

  @MessageMapping("/dms/{conversationId}/message")
  public void handleDirectMessage(
      @DestinationVariable UUID conversationId, DmPayload payload, Principal principal) {
    UUID senderUserId = UUID.fromString(principal.getName());
    var sender = userService.getUserById(senderUserId);
    DirectMessage saved = directMessageService.send(senderUserId, conversationId, payload.text());

    DirectConversation conv = directConversationRepository.findById(conversationId).orElseThrow();
    UUID otherId = conversationService.otherParticipant(conv, senderUserId);

    DmEvent event =
        new DmEvent(
            saved.getId(),
            saved.getConversationId(),
            saved.getSenderId(),
            sender.getUsername(),
            saved.getText(),
            saved.getCreatedAt());

    messagingTemplate.convertAndSendToUser(senderUserId.toString(), "/queue/dms", event);
    messagingTemplate.convertAndSendToUser(otherId.toString(), "/queue/dms", event);
  }
}
