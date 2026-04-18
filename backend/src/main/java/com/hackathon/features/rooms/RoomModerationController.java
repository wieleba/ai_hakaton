package com.hackathon.features.rooms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class RoomModerationController {
    private final RoomModerationService roomModerationService;
    private final UserService userService;

    record AdminRequest(UUID userId) {}

    record BanView(
        UUID bannedUserId,
        String bannedUsername,
        UUID bannedById,
        String bannedByUsername,
        OffsetDateTime bannedAt) {}

    private UUID currentUserId(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof UUID uuid) return uuid;
        return userService.getUserByUsername(authentication.getName()).getId();
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> kick(
        @PathVariable UUID roomId,
        @PathVariable UUID userId,
        Authentication authentication) {
        roomModerationService.kick(roomId, currentUserId(authentication), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admins")
    public ResponseEntity<Void> promoteAdmin(
        @PathVariable UUID roomId,
        @RequestBody AdminRequest body,
        Authentication authentication) {
        roomModerationService.promoteAdmin(roomId, currentUserId(authentication), body.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admins/{userId}")
    public ResponseEntity<Void> demoteAdmin(
        @PathVariable UUID roomId,
        @PathVariable UUID userId,
        Authentication authentication) {
        roomModerationService.demoteAdmin(roomId, currentUserId(authentication), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bans")
    public ResponseEntity<List<BanView>> listBans(
        @PathVariable UUID roomId, Authentication authentication) {
        UUID me = currentUserId(authentication);
        List<BanView> views =
            roomModerationService.listBans(roomId, me).stream()
                .map(
                    b -> {
                        User banned = userService.getUserById(b.getBannedUserId());
                        User by = userService.getUserById(b.getBannedById());
                        return new BanView(
                            b.getBannedUserId(),
                            banned.getUsername(),
                            b.getBannedById(),
                            by.getUsername(),
                            b.getBannedAt());
                    })
                .toList();
        return ResponseEntity.ok(views);
    }

    @DeleteMapping("/bans/{userId}")
    public ResponseEntity<Void> unban(
        @PathVariable UUID roomId,
        @PathVariable UUID userId,
        Authentication authentication) {
        roomModerationService.unban(roomId, currentUserId(authentication), userId);
        return ResponseEntity.noContent().build();
    }
}
