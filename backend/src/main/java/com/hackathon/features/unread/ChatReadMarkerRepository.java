package com.hackathon.features.unread;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReadMarkerRepository
    extends JpaRepository<ChatReadMarker, ChatReadMarker.Pk> {

  Optional<ChatReadMarker> findByUserIdAndChatTypeAndChatId(
      UUID userId, ChatType chatType, UUID chatId);

  List<ChatReadMarker> findByUserIdAndChatType(UUID userId, ChatType chatType);
}
