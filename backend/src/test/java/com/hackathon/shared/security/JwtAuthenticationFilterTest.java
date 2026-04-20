package com.hackathon.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import jakarta.servlet.FilterChain;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class JwtAuthenticationFilterTest {

  @Autowired private JwtAuthenticationFilter filter;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private UserRepository userRepository;
  @MockitoSpyBean private TokenRevocationService tokenRevocationService;

  @Test
  void revokedTokenDoesNotAuthenticate() throws Exception {
    SecurityContextHolder.clearContext();
    User u = new User();
    u.setUsername("revtest-" + System.nanoTime());
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash("x");
    u = userRepository.save(u);

    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());
    String hash = TokenHashing.sha256Hex(token);
    Mockito.doReturn(true).when(tokenRevocationService).isRevoked(hash);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = (request, response) -> {};

    filter.doFilter(req, resp, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void nonRevokedTokenAuthenticatesNormally() throws Exception {
    SecurityContextHolder.clearContext();
    User u = new User();
    u.setUsername("nonrev-" + System.nanoTime());
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash("x");
    u = userRepository.save(u);

    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = (request, response) -> {};

    filter.doFilter(req, resp, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  void tokenIssuedBeforePasswordChangeDoesNotAuthenticate() throws Exception {
    SecurityContextHolder.clearContext();
    User u = new User();
    u.setUsername("stale-" + System.nanoTime());
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash("x");
    u = userRepository.save(u);

    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());
    // Advance the user's password_changed_at to 10 seconds after the JWT's iat
    // (JWT iat precision is seconds; 10s buffer avoids the 1-second race window).
    u.setPasswordChangedAt(OffsetDateTime.now().plusSeconds(10));
    userRepository.save(u);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = (request, response) -> {};

    filter.doFilter(req, resp, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
