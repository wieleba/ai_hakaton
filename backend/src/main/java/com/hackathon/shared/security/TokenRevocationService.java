package com.hackathon.shared.security;

public interface TokenRevocationService {
  /** Mark a token hash as revoked for the given TTL in seconds. Safe to call with ttl <= 0 (no-op). */
  void revoke(String tokenHash, long ttlSeconds);

  /** True iff this token hash was revoked and hasn't yet expired. */
  boolean isRevoked(String tokenHash);
}
