package com.hackathon.features.rooms;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RoomMemberServiceTest {
  @Mock private RoomMemberRepository roomMemberRepository;

  private RoomMemberService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new RoomMemberService(roomMemberRepository);
  }

  @Test
  void testIsMember() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    when(roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

    boolean result = service.isMember(roomId, userId);

    assertTrue(result);
    verify(roomMemberRepository).existsByRoomIdAndUserId(roomId, userId);
  }

  @Test
  void testIsNotMember() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    when(roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

    boolean result = service.isMember(roomId, userId);

    assertFalse(result);
  }

  @Test
  void testAddMember() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    RoomMember member = create(RoomMember.class);
    when(roomMemberRepository.save(any(RoomMember.class))).thenReturn(member);

    service.addMember(roomId, userId);

    verify(roomMemberRepository).save(any(RoomMember.class));
  }

  @Test
  void testRemoveMember() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);

    service.removeMember(roomId, userId);

    verify(roomMemberRepository).deleteByRoomIdAndUserId(roomId, userId);
  }
}
