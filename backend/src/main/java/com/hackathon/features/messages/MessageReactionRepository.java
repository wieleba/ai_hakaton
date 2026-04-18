package com.hackathon.features.messages;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {
    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

    List<MessageReaction> findByMessageId(UUID messageId);

    void deleteByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);
}
