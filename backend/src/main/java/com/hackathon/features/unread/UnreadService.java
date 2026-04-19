package com.hackathon.features.unread;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnreadService {

  private final ChatReadMarkerRepository markerRepository;
  private final SimpMessagingTemplate messagingTemplate;

  @PersistenceContext private EntityManager em;

  public record UnreadCounts(Map<UUID, Long> rooms, Map<UUID, Long> dms) {}

  public record UnreadBump(String chatType, UUID chatId) {}

  @Transactional
  public void markRead(UUID userId, ChatType chatType, UUID chatId) {
    ChatReadMarker marker =
        markerRepository
            .findByUserIdAndChatTypeAndChatId(userId, chatType, chatId)
            .orElseGet(
                () ->
                    ChatReadMarker.builder()
                        .userId(userId)
                        .chatType(chatType)
                        .chatId(chatId)
                        .build());
    marker.setLastReadAt(OffsetDateTime.now());
    markerRepository.save(marker);
  }

  /**
   * Returns per-chat unread counts for this user. A "room unread" counts only messages
   * authored by others (sender != user) that aren't soft-deleted, in rooms the user is
   * currently a member of. Chats with zero unread are omitted.
   */
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public UnreadCounts counts(UUID userId) {
    List<Object[]> roomRows =
        em.createNativeQuery(
                "SELECT m.room_id, COUNT(*) "
                    + "FROM messages m "
                    + "JOIN room_members rm ON rm.room_id = m.room_id AND rm.user_id = :uid "
                    + "LEFT JOIN chat_read_markers cm "
                    + "  ON cm.user_id = :uid AND cm.chat_type = 'ROOM' AND cm.chat_id = m.room_id "
                    + "WHERE m.user_id <> :uid "
                    + "  AND m.deleted_at IS NULL "
                    + "  AND (cm.last_read_at IS NULL OR m.created_at > cm.last_read_at) "
                    + "GROUP BY m.room_id")
            .setParameter("uid", userId)
            .getResultList();

    List<Object[]> dmRows =
        em.createNativeQuery(
                "SELECT d.conversation_id, COUNT(*) "
                    + "FROM direct_messages d "
                    + "JOIN direct_conversations c ON c.id = d.conversation_id "
                    + "LEFT JOIN chat_read_markers cm "
                    + "  ON cm.user_id = :uid AND cm.chat_type = 'DM' AND cm.chat_id = d.conversation_id "
                    + "WHERE (c.user1_id = :uid OR c.user2_id = :uid) "
                    + "  AND d.sender_id <> :uid "
                    + "  AND d.deleted_at IS NULL "
                    + "  AND (cm.last_read_at IS NULL OR d.created_at > cm.last_read_at) "
                    + "GROUP BY d.conversation_id")
            .setParameter("uid", userId)
            .getResultList();

    Map<UUID, Long> rooms = new HashMap<>();
    for (Object[] row : roomRows) rooms.put((UUID) row[0], ((Number) row[1]).longValue());
    Map<UUID, Long> dms = new HashMap<>();
    for (Object[] row : dmRows) dms.put((UUID) row[0], ((Number) row[1]).longValue());
    return new UnreadCounts(rooms, dms);
  }

  /**
   * Push a lightweight "something new happened in this chat" hint to every recipient
   * except the sender. The frontend bumps its local unread count on receipt without
   * having to re-fetch the full list.
   */
  public void notifyBump(ChatType chatType, UUID chatId, List<UUID> recipientIds, UUID senderId) {
    UnreadBump payload = new UnreadBump(chatType.name(), chatId);
    for (UUID recipient : recipientIds) {
      if (recipient.equals(senderId)) continue;
      messagingTemplate.convertAndSendToUser(
          recipient.toString(), "/queue/unread", payload);
    }
  }
}
