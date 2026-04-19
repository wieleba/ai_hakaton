package com.hackathon.shared.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class RedisTokenRevocationService implements TokenRevocationService {
  private static final String KEY_PREFIX = "revoked_token:";

  private final StringRedisTemplate redis;

  @Override
  public void revoke(String tokenHash, long ttlSeconds) {
    if (tokenHash == null || tokenHash.isEmpty() || ttlSeconds <= 0) return;
    redis.opsForValue().set(KEY_PREFIX + tokenHash, "1", Duration.ofSeconds(ttlSeconds));
  }

  @Override
  public boolean isRevoked(String tokenHash) {
    if (tokenHash == null || tokenHash.isEmpty()) return false;
    Boolean present = redis.hasKey(KEY_PREFIX + tokenHash);
    return present != null && present;
  }
}
