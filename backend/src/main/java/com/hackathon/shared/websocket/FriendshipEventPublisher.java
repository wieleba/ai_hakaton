package com.hackathon.shared.websocket;

import com.hackathon.features.friendships.Friendship;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Publishes friendship state-change events to /user/{uuid}/queue/friend-events. */
@Component
@RequiredArgsConstructor
public class FriendshipEventPublisher {
  private final SimpMessagingTemplate messagingTemplate;

  public void publishRequestCreated(UUID addresseeUserId, Friendship request) {
    messagingTemplate.convertAndSendToUser(
        addresseeUserId.toString(),
        "/queue/friend-events",
        Map.of("type", "REQUEST_CREATED", "friendship", request));
  }

  public void publishAccepted(UUID forUserId, UUID counterpartUserId, Friendship friendship) {
    messagingTemplate.convertAndSendToUser(
        forUserId.toString(),
        "/queue/friend-events",
        Map.of(
            "type", "FRIENDSHIP_ACCEPTED",
            "counterpartUserId", counterpartUserId,
            "friendship", friendship));
  }

  public void publishRemoved(UUID forUserId, UUID counterpartUserId) {
    messagingTemplate.convertAndSendToUser(
        forUserId.toString(),
        "/queue/friend-events",
        Map.of("type", "FRIENDSHIP_REMOVED", "counterpartUserId", counterpartUserId));
  }
}
