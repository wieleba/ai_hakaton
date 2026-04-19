package com.hackathon.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationFilterExistenceTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void tokenForLiveUser_isAccepted() throws Exception {
    User u = register("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());

    // /api/rooms is an authenticated route that returns a 200 list
    mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void tokenForDeletedUser_is401() throws Exception {
    User u = register("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());
    userService.deleteAccount(u.getId());

    mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }
}
