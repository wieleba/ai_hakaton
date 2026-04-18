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
    // PostgreSQL's UUID "<" is a byte-wise unsigned comparison, equivalent
    // to lexicographic order of the canonical string form. Java's
    // UUID.compareTo is a SIGNED long comparison and disagrees with
    // PostgreSQL whenever a UUID's most significant bit is set (~50% of
    // randomly generated UUIDs), which would then violate the CHECK
    // (user1_id < user2_id) constraint. Compare strings instead.
    int cmp = userA.toString().compareTo(userB.toString());
    UUID user1 = cmp < 0 ? userA : userB;
    UUID user2 = cmp < 0 ? userB : userA;
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
