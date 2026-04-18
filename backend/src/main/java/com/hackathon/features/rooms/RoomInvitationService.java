package com.hackathon.features.rooms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomInvitationService {
  private final RoomInvitationRepository roomInvitationRepository;
  private final RoomMemberService roomMemberService;
  private final RoomBanRepository roomBanRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final UserService userService;

  @Transactional
  public RoomInvitation invite(UUID roomId, UUID inviterId, String inviteeUsername) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (!"private".equals(room.getVisibility())) {
      throw new IllegalArgumentException("Only private rooms need invitations");
    }
    User invitee = userService.getUserByUsername(inviteeUsername);
    UUID inviteeId = invitee.getId();

    if (inviteeId.equals(inviterId)) {
      throw new IllegalArgumentException("Cannot invite yourself");
    }
    if (!roomMemberService.isMember(roomId, inviterId)) {
      throw new IllegalArgumentException("Only members may invite others");
    }
    if (roomMemberService.isMember(roomId, inviteeId)) {
      throw new IllegalArgumentException("User is already a member of this room");
    }
    if (roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)) {
      throw new IllegalArgumentException("Cannot invite a banned user");
    }
    if (roomInvitationRepository.existsByRoomIdAndInviteeId(roomId, inviteeId)) {
      throw new IllegalArgumentException("An invitation for this user already exists");
    }

    return roomInvitationRepository.save(
        RoomInvitation.builder()
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build());
  }

  @Transactional
  public void accept(UUID invitationId, UUID currentUserId) {
    RoomInvitation inv = requireInvitation(invitationId);
    if (!inv.getInviteeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the invitee may accept");
    }
    if (roomBanRepository.existsByRoomIdAndBannedUserId(inv.getRoomId(), currentUserId)) {
      throw new IllegalArgumentException("You are banned from this room");
    }
    roomMemberService.addMember(inv.getRoomId(), currentUserId);
    roomInvitationRepository.delete(inv);
  }

  @Transactional
  public void decline(UUID invitationId, UUID currentUserId) {
    RoomInvitation inv = requireInvitation(invitationId);
    if (!inv.getInviteeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the invitee may decline");
    }
    roomInvitationRepository.delete(inv);
  }

  @Transactional
  public void cancel(UUID invitationId, UUID currentUserId) {
    RoomInvitation inv = requireInvitation(invitationId);
    boolean isInviter = inv.getInviterId().equals(currentUserId);
    boolean isAdmin = roomMemberService.isAdmin(inv.getRoomId(), currentUserId);
    if (!isInviter && !isAdmin) {
      throw new IllegalArgumentException("Only the inviter or a room admin may cancel");
    }
    roomInvitationRepository.delete(inv);
  }

  public List<RoomInvitation> listMyIncoming(UUID currentUserId) {
    return roomInvitationRepository.findByInviteeIdOrderByCreatedAtDesc(currentUserId);
  }

  public List<RoomInvitation> listOutgoingForRoom(UUID roomId, UUID currentUserId) {
    if (!roomMemberService.isMember(roomId, currentUserId)) {
      throw new IllegalArgumentException("Only members may view a room's outgoing invitations");
    }
    return roomInvitationRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
  }

  private RoomInvitation requireInvitation(UUID id) {
    return roomInvitationRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));
  }
}
