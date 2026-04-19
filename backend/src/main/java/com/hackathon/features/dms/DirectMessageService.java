package com.hackathon.features.dms;

import com.hackathon.features.attachments.AttachmentPolicy;
import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import com.hackathon.features.unread.ChatType;
import com.hackathon.features.unread.UnreadService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.AttachmentSummary;
import com.hackathon.shared.dto.DirectMessageDTO;
import com.hackathon.shared.dto.DirectMessageEventEnvelope;
import com.hackathon.shared.dto.MessagePreview;
import com.hackathon.shared.storage.StorageService;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {
  private static final int MAX_TEXT = 3072;

  private final DirectMessageRepository directMessageRepository;
  private final DirectConversationRepository directConversationRepository;
  private final ConversationService conversationService;
  private final FriendshipRepository friendshipRepository;
  private final UserBanRepository userBanRepository;
  private final UserService userService;
  private final SimpMessagingTemplate messagingTemplate;
  private final DirectMessageReactionRepository directMessageReactionRepository;
  private final DirectMessageAttachmentRepository attachmentRepository;
  private final StorageService storageService;
  private final UnreadService unreadService;

  @Transactional
  public DirectMessage send(UUID senderId, UUID conversationId, String text, UUID replyToId) {
    DirectConversation conv = loadConversation(conversationId);
    if (!conv.getUser1Id().equals(senderId) && !conv.getUser2Id().equals(senderId)) {
      throw new IllegalArgumentException("Not a participant of this conversation");
    }
    UUID other = conversationService.otherParticipant(conv, senderId);
    ensureFriendsAndNotBanned(senderId, other);
    validateText(text);
    if (replyToId != null) {
      DirectMessage parent = directMessageRepository
          .findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getConversationId().equals(conversationId)) {
        throw new IllegalArgumentException("Reply target is in a different conversation");
      }
    }
    DirectMessage saved = directMessageRepository.save(
        DirectMessage.builder()
            .conversationId(conversationId)
            .senderId(senderId)
            .text(text)
            .replyToId(replyToId)
            .build());
    publishToBoth(senderId, other, DirectMessageEventEnvelope.created(toDto(saved)));
    notifyDmUnread(senderId, other, conversationId);
    return saved;
  }

  public DirectMessage send(UUID senderId, UUID conversationId, String text) {
    return send(senderId, conversationId, text, null);
  }

  @Transactional
  public DirectMessage send(
      UUID senderId,
      UUID conversationId,
      String text,
      UUID replyToId,
      String filename,
      String mimeType,
      long size,
      InputStream content) {
    DirectConversation conv = loadConversation(conversationId);
    if (!conv.getUser1Id().equals(senderId) && !conv.getUser2Id().equals(senderId)) {
      throw new IllegalArgumentException("Not a participant of this conversation");
    }
    UUID other = conversationService.otherParticipant(conv, senderId);
    ensureFriendsAndNotBanned(senderId, other);

    boolean hasText = text != null && !text.trim().isEmpty();
    boolean hasFile = content != null;
    if (!hasText && !hasFile) {
      throw new IllegalArgumentException("Message must contain text or a file");
    }
    if (hasText && text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("Message exceeds 3072 characters");
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
      DirectMessage parent = directMessageRepository
          .findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getConversationId().equals(conversationId)) {
        throw new IllegalArgumentException("Reply target is in a different conversation");
      }
    }
    DirectMessage saved = directMessageRepository.save(
        DirectMessage.builder()
            .conversationId(conversationId)
            .senderId(senderId)
            .text(hasText ? text : "")
            .replyToId(replyToId)
            .build());

    if (hasFile) {
      String storageKey = storageService.store(content, size, mimeType);
      attachmentRepository.save(
          DirectMessageAttachment.builder()
              .directMessageId(saved.getId())
              .filename(filename)
              .mimeType(mimeType)
              .sizeBytes(size)
              .storageKey(storageKey)
              .build());
    }
    publishToBoth(senderId, other, DirectMessageEventEnvelope.created(toDto(saved)));
    notifyDmUnread(senderId, other, conversationId);
    return saved;
  }

  @Transactional
  public DirectMessage sendToUser(UUID senderId, UUID recipientId, String text) {
    ensureFriendsAndNotBanned(senderId, recipientId);
    DirectConversation conv = conversationService.getOrCreate(senderId, recipientId);
    return send(senderId, conv.getId(), text, null);
  }

  @Transactional
  public DirectMessage editMessage(UUID messageId, UUID callerId, String newText) {
    DirectMessage m = directMessageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getSenderId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can edit this message");
    }
    if (m.getDeletedAt() != null) {
      throw new IllegalArgumentException("Cannot edit a deleted message");
    }
    validateText(newText);
    m.setText(newText);
    m.setEditedAt(OffsetDateTime.now());
    DirectMessage saved = directMessageRepository.save(m);
    DirectConversation conv = loadConversation(saved.getConversationId());
    UUID other = conversationService.otherParticipant(conv, callerId);
    publishToBoth(callerId, other, DirectMessageEventEnvelope.edited(toDto(saved)));
    return saved;
  }

  @Transactional
  public void deleteMessage(UUID messageId, UUID callerId) {
    DirectMessage m = directMessageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getSenderId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can delete this message");
    }
    if (m.getDeletedAt() != null) {
      return;
    }
    attachmentRepository.findByDirectMessageId(messageId).ifPresent(att -> {
      storageService.delete(att.getStorageKey());
      attachmentRepository.delete(att);
    });
    m.setDeletedAt(OffsetDateTime.now());
    m.setDeletedBy(callerId);
    DirectMessage saved = directMessageRepository.save(m);
    DirectConversation conv = loadConversation(saved.getConversationId());
    UUID other = conversationService.otherParticipant(conv, callerId);
    publishToBoth(callerId, other, DirectMessageEventEnvelope.deleted(toDto(saved)));
  }

  public List<DirectMessage> getHistory(UUID conversationId, UUID beforeMessageId, int limit) {
    return beforeMessageId == null
        ? directMessageRepository.findByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequest.of(0, limit))
        : directMessageRepository.findByConversationIdBeforeCursor(
            conversationId, beforeMessageId, PageRequest.of(0, limit));
  }

  public Optional<DirectMessage> lastMessage(UUID conversationId) {
    return directMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId);
  }

  public List<DirectConversation> listConversations(UUID userId) {
    return directConversationRepository.findAllForUser(userId);
  }

  public DirectMessageDTO toDto(DirectMessage m) {
    return toDto(m, null);
  }

  public DirectMessageDTO toDto(DirectMessage m, UUID callerId) {
    String displayedText = m.getDeletedAt() == null ? m.getText() : null;
    String senderUsername = resolveUsername(m.getSenderId());
    MessagePreview preview = null;
    if (m.getReplyToId() != null) {
      Optional<DirectMessage> parent = directMessageRepository.findById(m.getReplyToId());
      if (parent.isPresent()) {
        DirectMessage p = parent.get();
        String snippet;
        if (p.getDeletedAt() != null) {
          snippet = MessagePreview.DELETED_PLACEHOLDER;
        } else {
          String t = p.getText() == null ? "" : p.getText();
          snippet = t.length() > MessagePreview.PREVIEW_CHARS
              ? t.substring(0, MessagePreview.PREVIEW_CHARS)
              : t;
        }
        preview = new MessagePreview(p.getId(), resolveUsername(p.getSenderId()), snippet);
      }
    }
    AttachmentSummary attachmentSummary = null;
    if (m.getDeletedAt() == null) {
      attachmentSummary = attachmentRepository.findByDirectMessageId(m.getId())
          .map(a -> new AttachmentSummary(a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes()))
          .orElse(null);
    }
    return DirectMessageDTO.builder()
        .id(m.getId())
        .conversationId(m.getConversationId())
        .senderId(m.getSenderId())
        .senderUsername(senderUsername)
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

  @Transactional
  public DirectMessageDTO toggleReaction(UUID messageId, UUID callerId, String emoji) {
    DirectMessage m = directMessageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (emoji == null || emoji.isBlank()) {
      throw new IllegalArgumentException("Emoji cannot be empty");
    }
    if (emoji.length() > 32) {
      throw new IllegalArgumentException("Emoji too long");
    }
    // Only participants may react
    DirectConversation conv = loadConversation(m.getConversationId());
    if (!conv.getUser1Id().equals(callerId) && !conv.getUser2Id().equals(callerId)) {
      throw new IllegalArgumentException("Not a participant of this conversation");
    }
    Optional<DirectMessageReaction> existing =
        directMessageReactionRepository.findByDirectMessageIdAndUserIdAndEmoji(messageId, callerId, emoji);
    if (existing.isPresent()) {
      directMessageReactionRepository.delete(existing.get());
    } else {
      directMessageReactionRepository.save(
          DirectMessageReaction.builder()
              .directMessageId(messageId)
              .userId(callerId)
              .emoji(emoji)
              .build());
    }
    DirectMessageDTO dto = toDto(m, callerId);
    UUID other = conversationService.otherParticipant(conv, callerId);
    publishToBoth(callerId, other, DirectMessageEventEnvelope.edited(toDto(m, null)));
    return dto;
  }

  private List<com.hackathon.shared.dto.ReactionSummary> buildReactions(UUID messageId, UUID callerId) {
    List<DirectMessageReaction> rows =
        directMessageReactionRepository.findByDirectMessageId(messageId);
    if (rows.isEmpty()) return List.of();
    Map<String, Integer> counts = new LinkedHashMap<>();
    Set<String> mine = new HashSet<>();
    for (DirectMessageReaction r : rows) {
      counts.merge(r.getEmoji(), 1, Integer::sum);
      if (callerId != null && callerId.equals(r.getUserId())) {
        mine.add(r.getEmoji());
      }
    }
    return counts.entrySet().stream()
        .map(e -> new com.hackathon.shared.dto.ReactionSummary(e.getKey(), e.getValue(), mine.contains(e.getKey())))
        .toList();
  }

  private DirectConversation loadConversation(UUID id) {
    return directConversationRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
  }

  private void validateText(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Message text cannot be empty");
    }
    if (text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("Message exceeds 3072 characters");
    }
  }

  private void ensureFriendsAndNotBanned(UUID me, UUID other) {
    friendshipRepository
        .findBetween(me, other)
        .filter(f -> Friendship.STATUS_ACCEPTED.equals(f.getStatus()))
        .orElseThrow(
            () -> new IllegalArgumentException("You must be friends to send a direct message"));
    if (userBanRepository.existsByBannerIdAndBannedId(me, other)
        || userBanRepository.existsByBannerIdAndBannedId(other, me)) {
      throw new IllegalArgumentException("Cannot send direct message");
    }
  }

  private void publishToBoth(UUID a, UUID b, DirectMessageEventEnvelope env) {
    messagingTemplate.convertAndSendToUser(a.toString(), "/queue/dms", env);
    messagingTemplate.convertAndSendToUser(b.toString(), "/queue/dms", env);
  }

  private void notifyDmUnread(UUID senderId, UUID otherId, UUID conversationId) {
    unreadService.notifyBump(ChatType.DM, conversationId, List.of(otherId), senderId);
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
