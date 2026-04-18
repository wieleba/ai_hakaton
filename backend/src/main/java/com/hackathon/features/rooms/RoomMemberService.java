package com.hackathon.features.rooms;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomMemberService {
  private final RoomMemberRepository roomMemberRepository;
  private final ChatRoomRepository chatRoomRepository;

  public boolean isMember(UUID roomId, UUID userId) {
    return roomMemberRepository.existsByRoomIdAndUserId(roomId, userId);
  }

  /**
   * True if the user is the room owner, or a member with role='admin'. Used as
   * the single permission gate for moderation actions.
   */
  public boolean isAdmin(UUID roomId, UUID userId) {
    if (isOwner(roomId, userId)) return true;
    return roomMemberRepository
        .findByRoomIdAndUserId(roomId, userId)
        .map(m -> RoomMember.ROLE_ADMIN.equals(m.getRole()))
        .orElse(false);
  }

  public boolean isOwner(UUID roomId, UUID userId) {
    return chatRoomRepository
        .findById(roomId)
        .map(room -> room.getOwnerId().equals(userId))
        .orElse(false);
  }

  public void addMember(UUID roomId, UUID userId) {
    RoomMember member =
        RoomMember.builder()
            .roomId(roomId)
            .userId(userId)
            .role(RoomMember.ROLE_MEMBER)
            .build();
    roomMemberRepository.save(member);
  }

  public void removeMember(UUID roomId, UUID userId) {
    roomMemberRepository.deleteByRoomIdAndUserId(roomId, userId);
  }

  public List<UUID> getMembers(UUID roomId) {
    return roomMemberRepository.findByRoomId(roomId).stream().map(RoomMember::getUserId).toList();
  }

  public List<RoomMember> listMembersWithRoles(UUID roomId) {
    return roomMemberRepository.findByRoomId(roomId);
  }

  public void setRole(UUID roomId, UUID userId, String role) {
    if (!RoomMember.ROLE_MEMBER.equals(role) && !RoomMember.ROLE_ADMIN.equals(role)) {
      throw new IllegalArgumentException("Invalid role: " + role);
    }
    RoomMember member =
        roomMemberRepository
            .findByRoomIdAndUserId(roomId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Not a member of this room"));
    member.setRole(role);
    roomMemberRepository.save(member);
  }
}
