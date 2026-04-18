package com.hackathon.features.rooms;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomModerationService {
    private final RoomMemberService roomMemberService;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomBanRepository roomBanRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public void kick(UUID roomId, UUID callerId, UUID targetId) {
        requireAdmin(roomId, callerId);
        if (callerId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot kick yourself — use Leave instead");
        }
        ChatRoom room = requireRoom(roomId);
        if (room.getOwnerId().equals(targetId)) {
            throw new IllegalArgumentException("Cannot kick the room owner");
        }
        roomMemberRepository.deleteByRoomIdAndUserId(roomId, targetId);
        RoomBan ban =
                RoomBan.builder()
                        .roomId(roomId)
                        .bannedUserId(targetId)
                        .bannedById(callerId)
                        .build();
        roomBanRepository.save(ban);
    }

    @Transactional
    public void promoteAdmin(UUID roomId, UUID callerId, UUID targetId) {
        requireAdmin(roomId, callerId);
        if (!roomMemberService.isMember(roomId, targetId)) {
            throw new IllegalArgumentException("Target is not a member of this room");
        }
        roomMemberService.setRole(roomId, targetId, RoomMember.ROLE_ADMIN);
    }

    @Transactional
    public void demoteAdmin(UUID roomId, UUID callerId, UUID targetId) {
        requireAdmin(roomId, callerId);
        if (callerId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot demote yourself");
        }
        ChatRoom room = requireRoom(roomId);
        if (room.getOwnerId().equals(targetId)) {
            throw new IllegalArgumentException("Cannot demote the room owner");
        }
        roomMemberService.setRole(roomId, targetId, RoomMember.ROLE_MEMBER);
    }

    @Transactional
    public void unban(UUID roomId, UUID callerId, UUID targetId) {
        requireAdmin(roomId, callerId);
        roomBanRepository.deleteByRoomIdAndBannedUserId(roomId, targetId);
    }

    public List<RoomBan> listBans(UUID roomId, UUID callerId) {
        requireAdmin(roomId, callerId);
        return roomBanRepository.findByRoomIdOrderByBannedAtDesc(roomId);
    }

    private void requireAdmin(UUID roomId, UUID callerId) {
        if (!roomMemberService.isAdmin(roomId, callerId)) {
            throw new IllegalArgumentException("Admin privilege required");
        }
    }

    private ChatRoom requireRoom(UUID roomId) {
        return chatRoomRepository
                .findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    }
}
