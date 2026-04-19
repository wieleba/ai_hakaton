package com.hackathon.features.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.features.presence.PresenceService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import com.hackathon.shared.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private PresenceService presenceService;
  @MockitoBean private SessionDisconnector sessionDisconnector;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;
  @Autowired private ObjectMapper objectMapper;

  /**
   * Creates a user with a generated UUID id via the repository and returns a JWT for them.
   * User.id is @GeneratedValue, so we save first and read back the generated id.
   */
  private record UserAndToken(UUID id, String token) {}

  private UserAndToken createUserWithToken(String suffix) {
    User u = new User();
    u.setUsername("user-" + suffix);
    u.setEmail(u.getUsername() + "@x.test");
    u.setPasswordHash("x");
    u = userRepository.save(u);
    return new UserAndToken(u.getId(), jwtTokenProvider.generateToken(u.getId(), u.getUsername()));
  }

  @Test
  void listUnauthenticatedIs401() throws Exception {
    mvc.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsSessionsWithCurrentFlag() throws Exception {
    var ut = createUserWithToken("list-" + System.nanoTime());
    presenceService.markOnline(ut.id(), "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(ut.id(), "s2", "UA2", "2.2.2.2", "h2");

    MvcResult result =
        mvc.perform(
                get("/api/sessions")
                    .header("Authorization", "Bearer " + ut.token())
                    .header("X-Session-Id", "s2"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString());
    boolean s1Current = false, s2Current = false;
    for (JsonNode n : arr) {
      if ("s1".equals(n.get("sessionId").asText())) s1Current = n.get("current").asBoolean();
      if ("s2".equals(n.get("sessionId").asText())) s2Current = n.get("current").asBoolean();
    }
    assertThat(s1Current).isFalse();
    assertThat(s2Current).isTrue();
  }

  @Test
  void logoutOwnSessionReturns204() throws Exception {
    var ut = createUserWithToken("del-" + System.nanoTime());
    presenceService.markOnline(ut.id(), "s1", "UA1", "1.1.1.1", "h1");

    mvc.perform(delete("/api/sessions/s1").header("Authorization", "Bearer " + ut.token()))
        .andExpect(status().isNoContent());
  }

  @Test
  void logoutUnknownSessionReturns404() throws Exception {
    var ut = createUserWithToken("404-" + System.nanoTime());

    mvc.perform(delete("/api/sessions/ghost").header("Authorization", "Bearer " + ut.token()))
        .andExpect(status().isNotFound());
  }

  @Test
  void logoutOthersWithoutHeaderReturns400() throws Exception {
    var ut = createUserWithToken("400-" + System.nanoTime());

    mvc.perform(delete("/api/sessions/others").header("Authorization", "Bearer " + ut.token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void logoutOthersRevokesSiblingsAndReturnsCount() throws Exception {
    var ut = createUserWithToken("others-" + System.nanoTime());
    presenceService.markOnline(ut.id(), "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(ut.id(), "s2", "UA2", "2.2.2.2", "h2");
    presenceService.markOnline(ut.id(), "s3", "UA3", "3.3.3.3", "h3");

    MvcResult result =
        mvc.perform(
                delete("/api/sessions/others")
                    .header("Authorization", "Bearer " + ut.token())
                    .header("X-Session-Id", "s2"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("revokedCount").asInt()).isEqualTo(2);
  }
}
