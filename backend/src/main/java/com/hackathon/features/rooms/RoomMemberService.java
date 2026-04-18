package com.hackathon.features.rooms;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomMemberService {
  private final RoomMemberRepository roomMemberRepository;

  public boolean isMember(UUID roomId, UUID userId) {
    return roomMemberRepository.existsByRoomIdAndUserId(roomId, userId);
  }

  public void addMember(UUID roomId, UUID userId) {
    RoomMember member = RoomMember.builder().roomId(roomId).userId(userId).build();
    roomMemberRepository.save(member);
  }

  public void removeMember(UUID roomId, UUID userId) {
    roomMemberRepository.deleteByRoomIdAndUserId(roomId, userId);
  }

  public List<UUID> getMembers(UUID roomId) {
    return roomMemberRepository.findByRoomId(roomId).stream()
        .map(RoomMember::getUserId)
        .toList();
  }
}
