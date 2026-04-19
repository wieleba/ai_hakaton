package com.hackathon.shared.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoopTokenRevocationService implements TokenRevocationService {
  @Override
  public void revoke(String tokenHash, long ttlSeconds) {}

  @Override
  public boolean isRevoked(String tokenHash) {
    return false;
  }
}
