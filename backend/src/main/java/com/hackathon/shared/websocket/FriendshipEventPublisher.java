package com.hackathon.shared.websocket;

import com.hackathon.features.friendships.Friendship;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Publishes friendship state-change events to /user/queue/friend-events. Real impl in Task 15. */
@Component
public class FriendshipEventPublisher {
  public void publishRequestCreated(UUID addresseeUserId, Friendship request) {
    // Implemented in Task 15
  }

  public void publishAccepted(UUID forUserId, UUID counterpartUserId, Friendship friendship) {
    // Implemented in Task 15
  }

  public void publishRemoved(UUID forUserId, UUID counterpartUserId) {
    // Implemented in Task 15
  }
}
