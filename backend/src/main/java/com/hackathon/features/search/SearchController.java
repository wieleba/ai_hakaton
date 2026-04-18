package com.hackathon.features.search;

import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
  private final SearchService searchService;
  private final UserService userService;

  record RoomHit(UUID id, String name, String description, String visibility) {}

  record UserHit(UUID id, String username) {}

  record SearchResponse(List<RoomHit> rooms, List<UserHit> users) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping
  public ResponseEntity<SearchResponse> search(
      @RequestParam("q") String q,
      @RequestParam(name = "limit", defaultValue = "5") int limit,
      Authentication authentication) {
    var result = searchService.search(q, currentUserId(authentication), limit);
    List<RoomHit> rooms =
        result.rooms().stream()
            .map(r -> new RoomHit(r.getId(), r.getName(), r.getDescription(), r.getVisibility()))
            .toList();
    List<UserHit> users =
        result.users().stream().map(u -> new UserHit(u.getId(), u.getUsername())).toList();
    return ResponseEntity.ok(new SearchResponse(rooms, users));
  }
}
