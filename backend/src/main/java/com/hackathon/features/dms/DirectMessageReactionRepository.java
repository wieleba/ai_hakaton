package com.hackathon.features.dms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageReactionRepository extends JpaRepository<DirectMessageReaction, UUID> {
  Optional<DirectMessageReaction> findByDirectMessageIdAndUserIdAndEmoji(
      UUID directMessageId, UUID userId, String emoji);

  List<DirectMessageReaction> findByDirectMessageId(UUID directMessageId);
}
