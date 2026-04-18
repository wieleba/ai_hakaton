package com.hackathon.features.rooms;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RoomInvitationServiceTest {
  @Mock private RoomInvitationRepository roomInvitationRepository;
  @Mock private RoomMemberService roomMemberService;
  @Mock private RoomBanRepository roomBanRepository;
  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private UserService userService;

  private RoomInvitationService service;
  private UUID roomId;
  private UUID inviterId;
  private UUID inviteeId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service =
        new RoomInvitationService(
            roomInvitationRepository,
            roomMemberService,
            roomBanRepository,
            chatRoomRepository,
            userService);
    roomId = create(UUID.class);
    inviterId = create(UUID.class);
    inviteeId = create(UUID.class);
  }

  private void stubPrivateRoom() {
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setVisibility("private");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
  }

  // --- invite ---

  @Test
  void invite_creates_pendingInvitation() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(false);
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(false);
    when(roomInvitationRepository.existsByRoomIdAndInviteeId(roomId, inviteeId)).thenReturn(false);
    when(roomInvitationRepository.save(any(RoomInvitation.class)))
        .thenAnswer(inv -> inv.getArgument(0, RoomInvitation.class));

    RoomInvitation inv = service.invite(roomId, inviterId, "bob");

    assertEquals(roomId, inv.getRoomId());
    assertEquals(inviterId, inv.getInviterId());
    assertEquals(inviteeId, inv.getInviteeId());
  }

  @Test
  void invite_rejectsPublicRoom() {
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("public");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsSelfInvite() {
    stubPrivateRoom();
    when(userService.getUserByUsername("me"))
        .thenReturn(User.builder().id(inviterId).username("me").build());

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "me"));
  }

  @Test
  void invite_rejectsNonMemberInviter() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsInviteeAlreadyMember() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsBannedInvitee() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(false);
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsDuplicatePending() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(false);
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(false);
    when(roomInvitationRepository.existsByRoomIdAndInviteeId(roomId, inviteeId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  // --- accept / decline / cancel ---

  @Test
  void accept_addsMembershipAndDeletesInvitation() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(false);

    service.accept(invId, inviteeId);

    verify(roomMemberService).addMember(roomId, inviteeId);
    verify(roomInvitationRepository).delete(inv);
  }

  @Test
  void accept_rejectsNonInvitee() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));

    assertThrows(
        IllegalArgumentException.class, () -> service.accept(invId, create(UUID.class)));
  }

  @Test
  void accept_rechecksBan() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.accept(invId, inviteeId));
  }

  @Test
  void decline_deletesInvitation() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));

    service.decline(invId, inviteeId);

    verify(roomInvitationRepository).delete(inv);
    verify(roomMemberService, never()).addMember(any(), any());
  }

  @Test
  void cancel_allowsInviter() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));

    service.cancel(invId, inviterId);

    verify(roomInvitationRepository).delete(inv);
  }

  @Test
  void cancel_allowsAdmin() {
    UUID invId = create(UUID.class);
    UUID adminId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    service.cancel(invId, adminId);

    verify(roomInvitationRepository).delete(inv);
  }

  @Test
  void cancel_rejectsOthers() {
    UUID invId = create(UUID.class);
    UUID strangerId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomMemberService.isAdmin(roomId, strangerId)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.cancel(invId, strangerId));
  }

  @Test
  void listMyIncoming_returnsInvitations() {
    when(roomInvitationRepository.findByInviteeIdOrderByCreatedAtDesc(inviteeId))
        .thenReturn(
            List.of(
                RoomInvitation.builder()
                    .id(create(UUID.class))
                    .roomId(roomId)
                    .inviteeId(inviteeId)
                    .build()));

    assertEquals(1, service.listMyIncoming(inviteeId).size());
  }
}
