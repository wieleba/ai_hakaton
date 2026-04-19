package com.hackathon.features.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class RedisPresencePublisher implements PresencePublisher {
  public static final String CHANNEL = "presence";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public record PresencePayload(String userId, String state) {}

  @Override
  public void publish(UUID userId, PresenceState state) {
    try {
      String body = objectMapper.writeValueAsString(new PresencePayload(userId.toString(), state.name()));
      redis.convertAndSend(CHANNEL, body);
    } catch (Exception e) {
      log.warn("Presence publish failed: {}", e.getMessage());
    }
  }
}
