package com.hackathon.features.attachments;

import com.hackathon.features.dms.DirectConversationRepository;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageAttachment;
import com.hackathon.features.dms.DirectMessageAttachmentRepository;
import com.hackathon.features.dms.DirectMessageRepository;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageAttachment;
import com.hackathon.features.messages.MessageAttachmentRepository;
import com.hackathon.features.messages.MessageRepository;
import com.hackathon.features.rooms.RoomMemberService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttachmentService {
  private final MessageAttachmentRepository messageAttachmentRepository;
  private final DirectMessageAttachmentRepository directMessageAttachmentRepository;
  private final MessageRepository messageRepository;
  private final DirectMessageRepository directMessageRepository;
  private final DirectConversationRepository directConversationRepository;
  private final RoomMemberService roomMemberService;

  public Optional<AttachmentLookupResult> lookup(UUID attachmentId) {
    Optional<MessageAttachment> room = messageAttachmentRepository.findById(attachmentId);
    if (room.isPresent()) {
      MessageAttachment a = room.get();
      Message parent = messageRepository.findById(a.getMessageId()).orElse(null);
      if (parent == null) return Optional.empty();
      return Optional.of(new AttachmentLookupResult(
          a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes(), a.getStorageKey(),
          AttachmentLookupResult.Scope.ROOM, parent.getRoomId()));
    }
    Optional<DirectMessageAttachment> dm = directMessageAttachmentRepository.findById(attachmentId);
    if (dm.isPresent()) {
      DirectMessageAttachment a = dm.get();
      DirectMessage parent = directMessageRepository.findById(a.getDirectMessageId()).orElse(null);
      if (parent == null) return Optional.empty();
      return Optional.of(new AttachmentLookupResult(
          a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes(), a.getStorageKey(),
          AttachmentLookupResult.Scope.DIRECT, parent.getConversationId()));
    }
    return Optional.empty();
  }

  public boolean isAuthorized(AttachmentLookupResult hit, UUID callerId) {
    if (hit.scope() == AttachmentLookupResult.Scope.ROOM) {
      return roomMemberService.isMember(hit.scopeId(), callerId);
    }
    return directConversationRepository
        .findById(hit.scopeId())
        .map(c -> c.getUser1Id().equals(callerId) || c.getUser2Id().equals(callerId))
        .orElse(false);
  }
}
