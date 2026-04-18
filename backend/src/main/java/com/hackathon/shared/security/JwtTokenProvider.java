package com.hackathon.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {
  @Value("${jwt.secret:my-secret-key-that-is-at-least-32-characters-long-for-HS256}")
  private String jwtSecret;

  @Value("${jwt.expiration:86400000}")
  private long jwtExpiration;

  public String generateToken(Integer userId, String email) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public Integer getUserIdFromToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Integer.parseInt(
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject());
  }

  public boolean validateToken(String token) {
    try {
      SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
