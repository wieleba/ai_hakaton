package com.hackathon.features.dms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
  @Query(
      value =
          "SELECT * FROM direct_messages WHERE conversation_id = ?1 ORDER BY created_at DESC",
      nativeQuery = true)
  List<DirectMessage> findByConversationIdOrderByCreatedAtDesc(
      UUID conversationId, Pageable pageable);

  @Query(
      value =
          "SELECT * FROM direct_messages WHERE conversation_id = ?1 AND created_at < "
              + "(SELECT created_at FROM direct_messages WHERE id = ?2) "
              + "ORDER BY created_at DESC",
      nativeQuery = true)
  List<DirectMessage> findByConversationIdBeforeCursor(
      UUID conversationId, UUID beforeMessageId, Pageable pageable);

  Optional<DirectMessage> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
