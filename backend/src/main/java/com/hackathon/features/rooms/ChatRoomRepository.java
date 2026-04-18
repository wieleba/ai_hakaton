package com.hackathon.features.rooms;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
  boolean existsByName(String name);

  Page<ChatRoom> findByVisibility(String visibility, Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      "SELECT r FROM ChatRoom r WHERE r.id IN "
          + "(SELECT m.roomId FROM RoomMember m WHERE m.userId = :userId) "
          + "ORDER BY r.updatedAt DESC")
  List<ChatRoom> findRoomsWhereUserIsMember(java.util.UUID userId);
}
