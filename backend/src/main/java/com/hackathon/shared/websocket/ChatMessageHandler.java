package com.hackathon.shared.websocket;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {
  private final MessageService messageService;
  private final UserService userService;

  @MessageMapping("/rooms/{roomId}/message")
  @SendTo("/topic/room/{roomId}")
  public ChatMessageDTO handleMessage(
      ChatMessageDTO payload, @DestinationVariable UUID roomId, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    var user = userService.getUserById(userId);
    Message saved = messageService.sendMessage(roomId, userId, payload.getText());
    return ChatMessageDTO.builder()
        .id(saved.getId())
        .roomId(saved.getRoomId())
        .userId(saved.getUserId())
        .username(user.getUsername())
        .text(saved.getText())
        .createdAt(saved.getCreatedAt())
        .build();
  }
}
