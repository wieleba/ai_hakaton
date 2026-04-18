package com.hackathon.features.rooms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {
  boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

  void deleteByRoomIdAndUserId(UUID roomId, UUID userId);

  List<RoomMember> findByRoomId(UUID roomId);
}
