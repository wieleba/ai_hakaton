package com.hackathon.features.dms;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageAttachmentRepository
    extends JpaRepository<DirectMessageAttachment, UUID> {
  Optional<DirectMessageAttachment> findByDirectMessageId(UUID directMessageId);
}
