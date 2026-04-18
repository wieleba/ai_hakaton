package com.hackathon.features.messages;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
  List<Message> findByRoomIdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);

  @Query("SELECT m FROM Message m WHERE m.roomId = :roomId AND m.createdAt < (SELECT m2.createdAt FROM Message m2 WHERE m2.id = :beforeId) ORDER BY m.createdAt DESC")
  List<Message> findByRoomIdBeforeCursor(
      @Param("roomId") UUID roomId,
      @Param("beforeId") UUID beforeId,
      Pageable pageable);

  long countByRoomId(UUID roomId);
}
