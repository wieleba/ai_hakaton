package com.hackathon.features.bans;

import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bans")
@RequiredArgsConstructor
public class UserBanController {
  private final UserBanService userBanService;
  private final UserService userService;

  record BanRequest(UUID userId) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping
  public ResponseEntity<UserBan> ban(@RequestBody BanRequest body, Authentication authentication) {
    return ResponseEntity.ok(userBanService.ban(currentUserId(authentication), body.userId()));
  }

  @GetMapping
  public ResponseEntity<List<UserBan>> list(Authentication authentication) {
    return ResponseEntity.ok(userBanService.listBans(currentUserId(authentication)));
  }
}
