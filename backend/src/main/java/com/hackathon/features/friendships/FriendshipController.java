package com.hackathon.features.friendships;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {
  private final FriendshipService friendshipService;
  private final UserService userService;

  record SendRequestBody(String username) {}

  record FriendView(UUID friendshipId, UUID userId, String username) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping("/requests")
  public ResponseEntity<Friendship> sendRequest(
      @RequestBody SendRequestBody body, Authentication authentication) {
    Friendship f = friendshipService.sendRequest(currentUserId(authentication), body.username());
    return ResponseEntity.ok(f);
  }

  @GetMapping("/requests")
  public ResponseEntity<List<Friendship>> listRequests(
      @RequestParam(defaultValue = "incoming") String direction, Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<Friendship> result =
        "outgoing".equals(direction)
            ? friendshipService.listPendingOutgoing(me)
            : friendshipService.listPendingIncoming(me);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/requests/{id}/accept")
  public ResponseEntity<Friendship> accept(@PathVariable UUID id, Authentication authentication) {
    return ResponseEntity.ok(friendshipService.accept(currentUserId(authentication), id));
  }

  @PostMapping("/requests/{id}/reject")
  public ResponseEntity<Void> reject(@PathVariable UUID id, Authentication authentication) {
    friendshipService.reject(currentUserId(authentication), id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/requests/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable UUID id, Authentication authentication) {
    friendshipService.cancel(currentUserId(authentication), id);
    return ResponseEntity.ok().build();
  }

  @GetMapping
  public ResponseEntity<List<FriendView>> listFriends(Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<Friendship> friendships = friendshipService.listAccepted(me);
    List<FriendView> views =
        friendships.stream()
            .map(
                f -> {
                  UUID otherId =
                      f.getRequesterId().equals(me) ? f.getAddresseeId() : f.getRequesterId();
                  User other = userService.getUserById(otherId);
                  return new FriendView(f.getId(), otherId, other.getUsername());
                })
            .toList();
    return ResponseEntity.ok(views);
  }

  @DeleteMapping("/{friendUserId}")
  public ResponseEntity<Void> removeFriend(
      @PathVariable UUID friendUserId, Authentication authentication) {
    friendshipService.removeFriend(currentUserId(authentication), friendUserId);
    return ResponseEntity.ok().build();
  }
}
