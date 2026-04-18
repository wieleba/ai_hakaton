package com.hackathon.features.rooms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomBanRepository extends JpaRepository<RoomBan, UUID> {
  boolean existsByRoomIdAndBannedUserId(UUID roomId, UUID bannedUserId);

  Optional<RoomBan> findByRoomIdAndBannedUserId(UUID roomId, UUID bannedUserId);

  List<RoomBan> findByRoomIdOrderByBannedAtDesc(UUID roomId);

  void deleteByRoomIdAndBannedUserId(UUID roomId, UUID bannedUserId);
}
