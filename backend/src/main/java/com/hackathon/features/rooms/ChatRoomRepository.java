package com.hackathon.features.rooms;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
  boolean existsByName(String name);

  Page<ChatRoom> findByVisibility(String visibility, Pageable pageable);
}
