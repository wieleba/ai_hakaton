package com.hackathon.features.presence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {
  private static final int MAX_BATCH = 200;
  private final PresenceService service;

  @GetMapping
  public ResponseEntity<Map<UUID, PresenceState>> snapshot(@RequestParam("userIds") List<UUID> userIds) {
    if (userIds.size() > MAX_BATCH) {
      userIds = userIds.subList(0, MAX_BATCH);
    }
    Map<UUID, PresenceState> raw = service.snapshot(userIds);
    Map<UUID, PresenceState> ordered = new HashMap<>(raw);
    return ResponseEntity.ok(ordered);
  }
}
