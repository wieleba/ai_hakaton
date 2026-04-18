package com.hackathon.features.messages;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hackathon.features.rooms.RoomMemberService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

class MessageServiceTest {
  @Mock private MessageRepository messageRepository;
  @Mock private RoomMemberService roomMemberService;

  private MessageService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new MessageService(messageRepository, roomMemberService);
  }

  @Test
  void testSendMessage() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    String text = "Hello world";
    when(roomMemberService.isMember(roomId, userId)).thenReturn(true);

    Message message = create(Message.class);
    message.setText(text);
    when(messageRepository.save(any(Message.class))).thenReturn(message);

    Message result = service.sendMessage(roomId, userId, text);

    assertNotNull(result);
    assertEquals(text, result.getText());
    verify(messageRepository).save(any());
  }

  @Test
  void testSendMessageNotMember() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    when(roomMemberService.isMember(roomId, userId)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.sendMessage(roomId, userId, "test"));
    verify(messageRepository, never()).save(any());
  }

  @Test
  void testSendMessageEmpty() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    when(roomMemberService.isMember(roomId, userId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.sendMessage(roomId, userId, ""));
  }

  @Test
  void testSendMessageTooLarge() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    String text = "x".repeat(3073);
    when(roomMemberService.isMember(roomId, userId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.sendMessage(roomId, userId, text));
  }

  @Test
  void testGetMessageHistory() {
    UUID roomId = create(UUID.class);
    List<Message> messages = List.of(create(Message.class), create(Message.class));
    when(messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 50)))
        .thenReturn(messages);

    List<Message> result = service.getMessageHistory(roomId, null, 50);

    assertEquals(2, result.size());
  }

  @Test
  void testGetMessageHistoryWithCursor() {
    UUID roomId = create(UUID.class);
    UUID beforeId = create(UUID.class);
    List<Message> messages = List.of(create(Message.class));
    when(messageRepository.findByRoomIdBeforeCursor(roomId, beforeId, PageRequest.of(0, 50)))
        .thenReturn(messages);

    List<Message> result = service.getMessageHistory(roomId, beforeId, 50);

    assertEquals(1, result.size());
  }
}
