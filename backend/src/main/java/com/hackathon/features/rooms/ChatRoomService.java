package com.hackathon.features.rooms;

import com.hackathon.features.unread.ChatReadMarkerRepository;
import com.hackathon.features.unread.ChatType;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
  private static final String VISIBILITY_PUBLIC = "public";
  private static final String VISIBILITY_PRIVATE = "private";

  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberService roomMemberService;
  private final UserService userService;
  private final RoomBanRepository roomBanRepository;
  private final ChatReadMarkerRepository chatReadMarkerRepository;

  @Transactional
  public ChatRoom createRoom(String name, String description, UUID userId, String visibility) {
    if (chatRoomRepository.existsByName(name)) {
      throw new IllegalArgumentException("Room name already exists");
    }
    String vis = visibility == null ? VISIBILITY_PUBLIC : visibility;
    if (!VISIBILITY_PUBLIC.equals(vis) && !VISIBILITY_PRIVATE.equals(vis)) {
      throw new IllegalArgumentException("Invalid visibility: " + vis);
    }
    ChatRoom room =
        ChatRoom.builder()
            .name(name)
            .description(description)
            .ownerId(userId)
            .visibility(vis)
            .build();
    ChatRoom savedRoom = chatRoomRepository.save(room);
    roomMemberService.addMember(savedRoom.getId(), userId);
    return savedRoom;
  }

  public Page<ChatRoom> listPublicRooms(int page, int limit) {
    return chatRoomRepository.findByVisibility(VISIBILITY_PUBLIC, PageRequest.of(page, limit));
  }

  public List<ChatRoom> listMyRooms(UUID userId) {
    return chatRoomRepository.findRoomsWhereUserIsMember(userId);
  }

  @Transactional
  public void joinRoom(UUID roomId, UUID userId) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (!VISIBILITY_PUBLIC.equals(room.getVisibility())) {
      throw new IllegalArgumentException("Cannot join private room");
    }
    if (roomBanRepository.existsByRoomIdAndBannedUserId(roomId, userId)) {
      throw new IllegalArgumentException("Cannot join this room");
    }
    if (roomMemberService.isMember(roomId, userId)) {
      return; // idempotent for re-entry after leave
    }
    roomMemberService.addMember(roomId, userId);
  }

  public void leaveRoom(UUID roomId, UUID userId) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (room.getOwnerId().equals(userId)) {
      throw new IllegalArgumentException("Owner cannot leave their own room");
    }
    roomMemberService.removeMember(roomId, userId);
  }

  @Transactional
  public void deleteRoom(UUID roomId, UUID userId) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (!room.getOwnerId().equals(userId)) {
      throw new IllegalArgumentException("Only the owner may delete this room");
    }
    // Clean up polymorphic read-markers explicitly — chat_read_markers.chat_id has
    // no FK to chat_rooms (it's shared with DMs), so cascade can't handle it.
    chatReadMarkerRepository.deleteAllForChat(ChatType.ROOM, roomId);
    chatRoomRepository.delete(room);
  }

  public ChatRoom getRoomById(UUID roomId) {
    return chatRoomRepository
        .findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
  }

  public List<User> listMembers(UUID roomId) {
    return roomMemberService.getMembers(roomId).stream()
        .map(userService::getUserById)
        .toList();
  }
}
