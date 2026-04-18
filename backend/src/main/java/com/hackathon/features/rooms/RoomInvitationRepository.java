package com.hackathon.features.rooms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, UUID> {
  boolean existsByRoomIdAndInviteeId(UUID roomId, UUID inviteeId);

  Optional<RoomInvitation> findByRoomIdAndInviteeId(UUID roomId, UUID inviteeId);

  List<RoomInvitation> findByRoomIdOrderByCreatedAtDesc(UUID roomId);

  List<RoomInvitation> findByInviteeIdOrderByCreatedAtDesc(UUID inviteeId);
}
