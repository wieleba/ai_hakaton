package com.hackathon.features.dms;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {
  private final DirectConversationRepository directConversationRepository;

  @Transactional
  public DirectConversation getOrCreate(UUID userA, UUID userB) {
    if (userA.equals(userB)) {
      throw new IllegalArgumentException("Cannot have a conversation with yourself");
    }
    UUID user1 = userA.compareTo(userB) < 0 ? userA : userB;
    UUID user2 = userA.compareTo(userB) < 0 ? userB : userA;
    return directConversationRepository
        .findByUser1IdAndUser2Id(user1, user2)
        .orElseGet(
            () ->
                directConversationRepository.save(
                    DirectConversation.builder().user1Id(user1).user2Id(user2).build()));
  }

  public UUID otherParticipant(DirectConversation conversation, UUID currentUserId) {
    return conversation.getUser1Id().equals(currentUserId)
        ? conversation.getUser2Id()
        : conversation.getUser1Id();
  }
}
