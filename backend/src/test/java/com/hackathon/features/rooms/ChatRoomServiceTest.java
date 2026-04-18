package com.hackathon.features.rooms;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ChatRoomServiceTest {
  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private RoomMemberService roomMemberService;
  @Mock private UserService userService;
  @Mock private RoomBanRepository roomBanRepository;

  private ChatRoomService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ChatRoomService(chatRoomRepository, roomMemberService, userService, roomBanRepository);
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

    ChatRoom result = service.createRoom(roomName, null, userId, null);

    assertNotNull(result);
    assertEquals(roomName, result.getName());
    verify(roomMemberService).addMember(result.getId(), userId);
  }

  @Test
  void testCreateRoomDuplicateName() {
    UUID userId = create(UUID.class);
    String roomName = "existing-room";
    when(chatRoomRepository.existsByName(roomName)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.createRoom(roomName, null, userId, null));
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
  void testJoinRoom_isNoOpWhenAlreadyMember() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("public");

    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
    when(roomMemberService.isMember(roomId, userId)).thenReturn(true);

    // No throw, no addMember call
    service.joinRoom(roomId, userId);

    verify(roomMemberService, never()).addMember(any(), any());
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

  @Test
  void createRoom_defaultsToPublicWhenVisibilityNull() {
    UUID userId = create(UUID.class);
    when(chatRoomRepository.existsByName("r1")).thenReturn(false);
    when(chatRoomRepository.save(any(ChatRoom.class)))
        .thenAnswer(inv -> inv.getArgument(0, ChatRoom.class));

    ChatRoom result = service.createRoom("r1", null, userId, null);

    assertEquals("public", result.getVisibility());
  }

  @Test
  void createRoom_acceptsPrivateVisibility() {
    UUID userId = create(UUID.class);
    when(chatRoomRepository.existsByName("r1")).thenReturn(false);
    when(chatRoomRepository.save(any(ChatRoom.class)))
        .thenAnswer(inv -> inv.getArgument(0, ChatRoom.class));

    ChatRoom result = service.createRoom("r1", null, userId, "private");

    assertEquals("private", result.getVisibility());
  }

  @Test
  void createRoom_rejectsInvalidVisibility() {
    UUID userId = create(UUID.class);
    when(chatRoomRepository.existsByName("r1")).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.createRoom("r1", null, userId, "secret"));
  }

  @Test
  void joinRoom_rejectsPrivateRoom() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("private");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    assertThrows(
        IllegalArgumentException.class, () -> service.joinRoom(roomId, userId));
  }

  @Test
  void joinRoom_rejectsWhenBanned() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("public");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, userId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.joinRoom(roomId, userId));
  }

  @Test
  void listMyRooms_returnsRoomsWhereUserIsMember() {
    UUID userId = create(UUID.class);
    ChatRoom r1 = create(ChatRoom.class);
    ChatRoom r2 = create(ChatRoom.class);
    when(chatRoomRepository.findRoomsWhereUserIsMember(userId)).thenReturn(List.of(r1, r2));

    assertEquals(2, service.listMyRooms(userId).size());
  }

  @Test
  void deleteRoom_onlyOwnerMayDelete() {
    UUID roomId = create(UUID.class);
    UUID ownerId = create(UUID.class);
    UUID someoneElse = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setOwnerId(ownerId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    assertThrows(
        IllegalArgumentException.class, () -> service.deleteRoom(roomId, someoneElse));
  }

  @Test
  void deleteRoom_ownerSucceeds() {
    UUID roomId = create(UUID.class);
    UUID ownerId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setOwnerId(ownerId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    service.deleteRoom(roomId, ownerId);

    verify(chatRoomRepository).delete(room);
  }
}
