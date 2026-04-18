package com.hackathon.features.rooms;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RoomModerationServiceTest {
  @Mock private RoomMemberService roomMemberService;
  @Mock private RoomMemberRepository roomMemberRepository;
  @Mock private RoomBanRepository roomBanRepository;
  @Mock private ChatRoomRepository chatRoomRepository;

  private RoomModerationService service;
  private UUID roomId;
  private UUID adminId;
  private UUID targetId;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service =
        new RoomModerationService(
            roomMemberService, roomMemberRepository, roomBanRepository, chatRoomRepository);
    roomId = create(UUID.class);
    adminId = create(UUID.class);
    targetId = create(UUID.class);
    ownerId = create(UUID.class);
  }

  private void stubRoom(UUID withOwnerId) {
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setOwnerId(withOwnerId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
  }

  // --- kick ---

  @Test
  void kick_adminRemovesMemberAndCreatesBanRow() {
    stubRoom(ownerId);
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);
    when(roomBanRepository.save(any(RoomBan.class)))
        .thenAnswer(inv -> inv.getArgument(0, RoomBan.class));

    service.kick(roomId, adminId, targetId);

    verify(roomMemberRepository).deleteByRoomIdAndUserId(roomId, targetId);
    verify(roomBanRepository).save(any(RoomBan.class));
  }

  @Test
  void kick_rejectsNonAdmin() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> service.kick(roomId, adminId, targetId));
    verify(roomMemberRepository, never()).deleteByRoomIdAndUserId(any(), any());
  }

  @Test
  void kick_rejectsKickingOwner() {
    stubRoom(targetId); // target is the owner
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.kick(roomId, adminId, targetId));
  }

  @Test
  void kick_rejectsSelfKick() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.kick(roomId, adminId, adminId));
  }

  // --- promote / demote ---

  @Test
  void promoteAdmin_adminGrantsRole() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, targetId)).thenReturn(true);

    service.promoteAdmin(roomId, adminId, targetId);

    verify(roomMemberService).setRole(roomId, targetId, RoomMember.ROLE_ADMIN);
  }

  @Test
  void promoteAdmin_rejectsIfTargetNotMember() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, targetId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.promoteAdmin(roomId, adminId, targetId));
  }

  @Test
  void demoteAdmin_adminRevokesRole() {
    stubRoom(ownerId);
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    service.demoteAdmin(roomId, adminId, targetId);

    verify(roomMemberService).setRole(roomId, targetId, RoomMember.ROLE_MEMBER);
  }

  @Test
  void demoteAdmin_rejectsDemotingOwner() {
    stubRoom(targetId); // target IS the owner
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.demoteAdmin(roomId, adminId, targetId));
  }

  @Test
  void demoteAdmin_rejectsSelfDemote() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.demoteAdmin(roomId, adminId, adminId));
  }

  // --- unban ---

  @Test
  void unban_adminDeletesBanRow() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    service.unban(roomId, adminId, targetId);

    verify(roomBanRepository).deleteByRoomIdAndBannedUserId(roomId, targetId);
  }

  @Test
  void unban_rejectsNonAdmin() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> service.unban(roomId, adminId, targetId));
  }
}
