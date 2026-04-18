package com.hackathon.shared.websocket;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.shared.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {
  private final MessageService messageService;

  @MessageMapping("/rooms/{roomId}/message")
  @SendTo("/topic/room/{roomId}")
  public ChatMessageDTO handleMessage(
      ChatMessageDTO payload,
      @DestinationVariable UUID roomId,
      Principal principal) {
    UUID userId = UUID.randomUUID();
    Message savedMessage = messageService.sendMessage(roomId, userId, payload.getText());

    return ChatMessageDTO.builder()
        .id(savedMessage.getId())
        .roomId(savedMessage.getRoomId())
        .userId(savedMessage.getUserId())
        .username(principal.getName())
        .text(savedMessage.getText())
        .createdAt(savedMessage.getCreatedAt())
        .build();
  }
}
