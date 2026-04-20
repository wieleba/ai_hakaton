package com.hackathon.shared.security;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;
  private final TokenRevocationService tokenRevocationService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (jwtTokenProvider.validateToken(token)
          && !tokenRevocationService.isRevoked(TokenHashing.sha256Hex(token))) {
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        // Guard: the user row may be gone (account deletion). Leave the context
        // unauthenticated so the SecurityConfig entry point returns 401 on protected
        // routes; public routes (/register, /login) continue to work.
        Optional<User> maybeUser = userRepository.findById(userId);
        if (maybeUser.isPresent() && tokenIssuedAfterPasswordChange(token, maybeUser.get())) {
          String username = jwtTokenProvider.getUsernameFromToken(token);
          UsernamePasswordAuthenticationToken auth =
              new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
          auth.setDetails(userId);
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    }
    chain.doFilter(request, response);
  }

  /**
   * Reject tokens issued before the user's password was last (re)set. Comparison is
   * in epoch seconds (JWT `iat` precision): a token whose `iat` is strictly less
   * than the password-change second is rejected. Tokens issued in the same second
   * as the password change are accepted — a 1-second race window we accept since
   * the attacker would need to have minted a token within that exact second.
   */
  private boolean tokenIssuedAfterPasswordChange(String token, User user) {
    if (user.getPasswordChangedAt() == null) return true; // legacy row: don't lock out
    long iat = jwtTokenProvider.getIssuedAtEpochSeconds(token);
    if (iat == 0L) return false;
    long changedAt = user.getPasswordChangedAt().toEpochSecond();
    return iat >= changedAt;
  }
}
