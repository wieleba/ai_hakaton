package com.hackathon.features.dms;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

  @Transactional
  public DirectMessage send(UUID senderId, UUID conversationId, String text) {
    DirectConversation conv =
        directConversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
    if (!conv.getUser1Id().equals(senderId) && !conv.getUser2Id().equals(senderId)) {
      throw new IllegalArgumentException("Not a participant of this conversation");
    }
    UUID other = conversationService.otherParticipant(conv, senderId);
    ensureFriendsAndNotBanned(senderId, other);
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Message text cannot be empty");
    }
    if (text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("Message exceeds 3072 characters");
    }
    return directMessageRepository.save(
        DirectMessage.builder()
            .conversationId(conversationId)
            .senderId(senderId)
            .text(text)
            .build());
  }

  @Transactional
  public DirectMessage sendToUser(UUID senderId, UUID recipientId, String text) {
    ensureFriendsAndNotBanned(senderId, recipientId);
    DirectConversation conv = conversationService.getOrCreate(senderId, recipientId);
    return send(senderId, conv.getId(), text);
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

  private void ensureFriendsAndNotBanned(UUID me, UUID other) {
    friendshipRepository
        .findBetween(me, other)
        .filter(f -> Friendship.STATUS_ACCEPTED.equals(f.getStatus()))
        .orElseThrow(
            () ->
                new IllegalArgumentException("You must be friends to send a direct message"));
    if (userBanRepository.existsByBannerIdAndBannedId(me, other)
        || userBanRepository.existsByBannerIdAndBannedId(other, me)) {
      throw new IllegalArgumentException("Cannot send direct message");
    }
  }
}
