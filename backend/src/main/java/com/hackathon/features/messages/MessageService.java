package com.hackathon.features.messages;

import com.hackathon.features.attachments.AttachmentPolicy;
import com.hackathon.features.rooms.RoomMemberService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.AttachmentSummary;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.dto.MessageEventEnvelope;
import com.hackathon.shared.dto.MessagePreview;
import com.hackathon.shared.dto.ReactionSummary;
import com.hackathon.shared.storage.StorageService;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {
  private static final int MAX_MESSAGE_SIZE = 3072;

  private final MessageRepository messageRepository;
  private final MessageReactionRepository messageReactionRepository;
  private final MessageAttachmentRepository attachmentRepository;
  private final RoomMemberService roomMemberService;
  private final UserService userService;
  private final SimpMessagingTemplate messagingTemplate;
  private final StorageService storageService;

  @Transactional
  public Message sendMessage(UUID roomId, UUID userId, String text, UUID replyToId) {
    if (!roomMemberService.isMember(roomId, userId)) {
      throw new IllegalArgumentException("User is not a member of this room");
    }
    validateText(text);
    if (replyToId != null) {
      Message parent = messageRepository.findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getRoomId().equals(roomId)) {
        throw new IllegalArgumentException("Reply target is in a different room");
      }
    }
    Message saved = messageRepository.save(
        Message.builder()
            .roomId(roomId)
            .userId(userId)
            .text(text)
            .replyToId(replyToId)
            .build());
    publish(MessageEventEnvelope.created(toDto(saved)));
    return saved;
  }

  /** Overload used by callers that never reply (tests, internal). */
  public Message sendMessage(UUID roomId, UUID userId, String text) {
    return sendMessage(roomId, userId, text, null);
  }

  @Transactional
  public Message sendMessage(
      UUID roomId,
      UUID userId,
      String text,
      UUID replyToId,
      String filename,
      String mimeType,
      long size,
      InputStream content) {
    if (!roomMemberService.isMember(roomId, userId)) {
      throw new IllegalArgumentException("User is not a member of this room");
    }
    boolean hasText = text != null && !text.trim().isEmpty();
    boolean hasFile = content != null;
    if (!hasText && !hasFile) {
      throw new IllegalArgumentException("Message must contain text or a file");
    }
    if (hasText && text.length() > MAX_MESSAGE_SIZE) {
      throw new IllegalArgumentException(
          "Message exceeds maximum size of " + MAX_MESSAGE_SIZE + " bytes");
    }
    if (hasFile) {
      if (!AttachmentPolicy.isAllowed(mimeType)) {
        throw new IllegalArgumentException("File type not allowed: " + mimeType);
      }
      if (size <= 0 || size > AttachmentPolicy.MAX_SIZE_BYTES) {
        throw new IllegalArgumentException("File size outside allowed range");
      }
    }
    if (replyToId != null) {
      Message parent = messageRepository
          .findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getRoomId().equals(roomId)) {
        throw new IllegalArgumentException("Reply target is in a different room");
      }
    }
    Message saved = messageRepository.save(
        Message.builder()
            .roomId(roomId)
            .userId(userId)
            .text(hasText ? text : "")
            .replyToId(replyToId)
            .build());

    if (hasFile) {
      String storageKey = storageService.store(content, size, mimeType);
      attachmentRepository.save(
          MessageAttachment.builder()
              .messageId(saved.getId())
              .filename(filename)
              .mimeType(mimeType)
              .sizeBytes(size)
              .storageKey(storageKey)
              .build());
    }

    publish(MessageEventEnvelope.created(toDto(saved)));
    return saved;
  }

  @Transactional
  public Message editMessage(UUID messageId, UUID callerId, String newText) {
    Message m = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getUserId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can edit this message");
    }
    if (m.getDeletedAt() != null) {
      throw new IllegalArgumentException("Cannot edit a deleted message");
    }
    validateText(newText);
    m.setText(newText);
    m.setEditedAt(OffsetDateTime.now());
    Message saved = messageRepository.save(m);
    publish(MessageEventEnvelope.edited(toDto(saved)));
    return saved;
  }

  @Transactional
  public void deleteMessage(UUID messageId, UUID callerId) {
    Message m = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getUserId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can delete this message");
    }
    if (m.getDeletedAt() != null) {
      return; // idempotent
    }
    attachmentRepository.findByMessageId(messageId).ifPresent(att -> {
      storageService.delete(att.getStorageKey());
      attachmentRepository.delete(att);
    });
    m.setDeletedAt(OffsetDateTime.now());
    m.setDeletedBy(callerId);
    Message saved = messageRepository.save(m);
    publish(MessageEventEnvelope.deleted(toDto(saved)));
  }

  public List<Message> getMessageHistory(UUID roomId, UUID beforeMessageId, int limit) {
    if (beforeMessageId == null) {
      return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, limit));
    } else {
      return messageRepository.findByRoomIdBeforeCursor(roomId, beforeMessageId, PageRequest.of(0, limit));
    }
  }

  public long getMessageCount(UUID roomId) {
    return messageRepository.countByRoomId(roomId);
  }

  /** Build the outbound DTO, blanking text on tombstones and resolving the reply preview. */
  public ChatMessageDTO toDto(Message m) {
    return toDto(m, null);
  }

  public ChatMessageDTO toDto(Message m, UUID callerId) {
    String displayedText = m.getDeletedAt() == null ? m.getText() : null;
    String username = resolveUsername(m.getUserId());
    MessagePreview preview = null;
    if (m.getReplyToId() != null) {
      Optional<Message> parent = messageRepository.findById(m.getReplyToId());
      if (parent.isPresent()) {
        Message p = parent.get();
        String snippet;
        if (p.getDeletedAt() != null) {
          snippet = MessagePreview.DELETED_PLACEHOLDER;
        } else {
          String t = p.getText() == null ? "" : p.getText();
          snippet = t.length() > MessagePreview.PREVIEW_CHARS
              ? t.substring(0, MessagePreview.PREVIEW_CHARS)
              : t;
        }
        preview = new MessagePreview(p.getId(), resolveUsername(p.getUserId()), snippet);
      }
    }
    AttachmentSummary attachmentSummary = null;
    if (m.getDeletedAt() == null) {
      attachmentSummary = attachmentRepository.findByMessageId(m.getId())
          .map(a -> new AttachmentSummary(a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes()))
          .orElse(null);
    }
    return ChatMessageDTO.builder()
        .id(m.getId())
        .roomId(m.getRoomId())
        .userId(m.getUserId())
        .username(username)
        .text(displayedText)
        .createdAt(m.getCreatedAt())
        .editedAt(m.getEditedAt())
        .deletedAt(m.getDeletedAt())
        .deletedBy(m.getDeletedBy())
        .replyTo(preview)
        .reactions(buildReactions(m.getId(), callerId))
        .attachment(attachmentSummary)
        .build();
  }

  /** Toggle a reaction for the caller on a message. Returns the updated message DTO. */
  @Transactional
  public ChatMessageDTO toggleReaction(UUID messageId, UUID callerId, String emoji) {
    Message m = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (emoji == null || emoji.isBlank()) {
      throw new IllegalArgumentException("Emoji cannot be empty");
    }
    if (emoji.length() > 32) {
      throw new IllegalArgumentException("Emoji too long");
    }
    Optional<MessageReaction> existing =
        messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, callerId, emoji);
    if (existing.isPresent()) {
      messageReactionRepository.delete(existing.get());
    } else {
      messageReactionRepository.save(
          MessageReaction.builder().messageId(messageId).userId(callerId).emoji(emoji).build());
    }
    ChatMessageDTO dto = toDto(m, callerId);
    // Broadcast with reactedByMe=false (consumers local-reconcile their own flag).
    publish(MessageEventEnvelope.edited(toDto(m, null)));
    return dto;
  }

  private List<ReactionSummary> buildReactions(UUID messageId, UUID callerId) {
    List<MessageReaction> rows = messageReactionRepository.findByMessageId(messageId);
    if (rows.isEmpty()) return List.of();
    // Aggregate counts per emoji + reactedByMe flag
    java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
    java.util.Set<String> mine = new java.util.HashSet<>();
    for (MessageReaction r : rows) {
      counts.merge(r.getEmoji(), 1, Integer::sum);
      if (callerId != null && callerId.equals(r.getUserId())) {
        mine.add(r.getEmoji());
      }
    }
    return counts.entrySet().stream()
        .map(e -> new ReactionSummary(e.getKey(), e.getValue(), mine.contains(e.getKey())))
        .toList();
  }

  private void validateText(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Message text cannot be empty");
    }
    if (text.length() > MAX_MESSAGE_SIZE) {
      throw new IllegalArgumentException(
          "Message exceeds maximum size of " + MAX_MESSAGE_SIZE + " bytes");
    }
  }

  private void publish(MessageEventEnvelope env) {
    messagingTemplate.convertAndSend("/topic/room/" + env.message().getRoomId(), env);
  }

  private String resolveUsername(UUID userId) {
    if (userId == null) return "Deleted user";
    try {
      User u = userService.getUserById(userId);
      if (u == null) return userId.toString().substring(0, 8);
      return u.getUsername();
    } catch (IllegalArgumentException e) {
      return userId.toString().substring(0, 8);
    }
  }
}
