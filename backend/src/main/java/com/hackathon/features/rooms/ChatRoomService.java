package com.hackathon.features.rooms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberService roomMemberService;
  private final UserService userService;

  public ChatRoom createRoom(String name, String description, UUID userId) {
    if (chatRoomRepository.existsByName(name)) {
      throw new IllegalArgumentException("Room name already exists");
    }
    ChatRoom room = ChatRoom.builder()
        .name(name)
        .description(description)
        .ownerId(userId)
        .visibility("public")
        .build();
    ChatRoom savedRoom = chatRoomRepository.save(room);
    roomMemberService.addMember(savedRoom.getId(), userId);
    return savedRoom;
  }

  public Page<ChatRoom> listPublicRooms(int page, int limit) {
    return chatRoomRepository.findByVisibility("public", PageRequest.of(page, limit));
  }

  public void joinRoom(UUID roomId, UUID userId) {
    ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (!"public".equals(room.getVisibility())) {
      throw new IllegalArgumentException("Cannot join private room");
    }
    if (roomMemberService.isMember(roomId, userId)) {
      throw new IllegalArgumentException("Already a member of this room");
    }
    roomMemberService.addMember(roomId, userId);
  }

  public void leaveRoom(UUID roomId, UUID userId) {
    ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (room.getOwnerId().equals(userId)) {
      throw new IllegalArgumentException("Owner cannot leave their own room");
    }
    roomMemberService.removeMember(roomId, userId);
  }

  public ChatRoom getRoomById(UUID roomId) {
    return chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
  }

  public List<User> listMembers(UUID roomId) {
    return roomMemberService.getMembers(roomId).stream()
        .map(userId -> userService.getUserById(userId))
        .toList();
  }
}
