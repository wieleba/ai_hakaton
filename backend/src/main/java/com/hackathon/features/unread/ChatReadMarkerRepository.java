package com.hackathon.features.unread;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatReadMarkerRepository
    extends JpaRepository<ChatReadMarker, ChatReadMarker.Pk> {

  Optional<ChatReadMarker> findByUserIdAndChatTypeAndChatId(
      UUID userId, ChatType chatType, UUID chatId);

  List<ChatReadMarker> findByUserIdAndChatType(UUID userId, ChatType chatType);

  /** Bulk-delete all markers for one chat, used when the chat itself is deleted. */
  @Modifying
  @Transactional
  @Query("DELETE FROM ChatReadMarker m WHERE m.chatType = :chatType AND m.chatId = :chatId")
  int deleteAllForChat(ChatType chatType, UUID chatId);
}
