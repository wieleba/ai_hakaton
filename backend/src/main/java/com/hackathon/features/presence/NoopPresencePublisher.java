package com.hackathon.features.presence;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class NoopPresencePublisher implements PresencePublisher {
  @Override
  public void publish(UUID userId, PresenceState state) {
    // No-op: tests don't exercise cross-instance fan-out.
  }
}
