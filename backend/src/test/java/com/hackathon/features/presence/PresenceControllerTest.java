package com.hackathon.features.presence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PresenceControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired JwtTokenProvider jwtTokenProvider;
  @Autowired PresenceService presenceService;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void snapshot_returnsStatePerId() throws Exception {
    User alice = register("a");
    User bob = register("b");
    User carol = register("c");
    String token = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());

    presenceService.markOnline(alice.getId(), "s1");
    presenceService.markOnline(bob.getId(), "s1");
    presenceService.markAfk(bob.getId(), "s1");

    mvc.perform(
            get("/api/presence")
                .param("userIds", alice.getId().toString(), bob.getId().toString(), carol.getId().toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$." + alice.getId()).value("ONLINE"))
        .andExpect(jsonPath("$." + bob.getId()).value("AFK"))
        .andExpect(jsonPath("$." + carol.getId()).value("OFFLINE"));
  }

  @Test
  void snapshot_noJwt_401() throws Exception {
    mvc.perform(get("/api/presence").param("userIds", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized());
  }
}
