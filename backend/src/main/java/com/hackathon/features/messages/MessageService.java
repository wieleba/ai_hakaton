package com.hackathon.features.messages;

import com.hackathon.features.rooms.RoomMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {
  private final MessageRepository messageRepository;
  private final RoomMemberService roomMemberService;

  private static final int MAX_MESSAGE_SIZE = 3072;

  public Message sendMessage(UUID roomId, UUID userId, String text) {
    if (!roomMemberService.isMember(roomId, userId)) {
      throw new IllegalArgumentException("User is not a member of this room");
    }

    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Message text cannot be empty");
    }

    if (text.length() > MAX_MESSAGE_SIZE) {
      throw new IllegalArgumentException("Message exceeds maximum size of " + MAX_MESSAGE_SIZE + " bytes");
    }

    Message message = Message.builder()
        .roomId(roomId)
        .userId(userId)
        .text(text)
        .build();

    return messageRepository.save(message);
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
}
