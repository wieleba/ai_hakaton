package com.hackathon.shared.security;

import com.hackathon.features.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
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

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (jwtTokenProvider.validateToken(token)) {
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        // Guard: the user row may be gone (account deletion). Leave the context
        // unauthenticated so the SecurityConfig entry point returns 401 on protected
        // routes; public routes (/register, /login) continue to work.
        if (userRepository.existsById(userId)) {
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
}
