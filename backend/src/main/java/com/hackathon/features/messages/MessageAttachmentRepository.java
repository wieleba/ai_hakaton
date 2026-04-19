package com.hackathon.features.messages;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
  Optional<MessageAttachment> findByMessageId(UUID messageId);
}
