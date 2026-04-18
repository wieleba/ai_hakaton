package com.hackathon.features.rooms;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ChatRoomServiceTest {
  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private RoomMemberService roomMemberService;

  private ChatRoomService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ChatRoomService(chatRoomRepository, roomMemberService);
  }

  @Test
  void testCreateRoom() {
    UUID userId = create(UUID.class);
    UUID roomId = create(UUID.class);
    String roomName = "test-room";
    when(chatRoomRepository.existsByName(roomName)).thenReturn(false);

    ChatRoom room = create(ChatRoom.class);
    room.setName(roomName);
    when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(room);

    ChatRoom result = service.createRoom(roomName, null, userId);

    assertNotNull(result);
    assertEquals(roomName, result.getName());
    verify(roomMemberService).addMember(result.getId(), userId);
  }

  @Test
  void testCreateRoomDuplicateName() {
    UUID userId = create(UUID.class);
    String roomName = "existing-room";
    when(chatRoomRepository.existsByName(roomName)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.createRoom(roomName, null, userId));
    verify(chatRoomRepository, never()).save(any());
  }

  @Test
  void testJoinRoom() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("public");

    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
    when(roomMemberService.isMember(roomId, userId)).thenReturn(false);

    service.joinRoom(roomId, userId);

    verify(roomMemberService).addMember(roomId, userId);
  }

  @Test
  void testLeaveRoom() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    UUID ownerId = create(UUID.class);

    ChatRoom room = create(ChatRoom.class);
    room.setOwnerId(ownerId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    service.leaveRoom(roomId, userId);

    verify(roomMemberService).removeMember(roomId, userId);
  }

  @Test
  void testLeaveRoomAsOwner() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);

    ChatRoom room = create(ChatRoom.class);
    room.setOwnerId(userId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    assertThrows(IllegalArgumentException.class, () -> service.leaveRoom(roomId, userId));
    verify(roomMemberService, never()).removeMember(any(), any());
  }
}
