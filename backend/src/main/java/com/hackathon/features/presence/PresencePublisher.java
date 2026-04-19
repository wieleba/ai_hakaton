package com.hackathon.features.presence;

import java.util.UUID;

public interface PresencePublisher {
  void publish(UUID userId, PresenceState state);
}
