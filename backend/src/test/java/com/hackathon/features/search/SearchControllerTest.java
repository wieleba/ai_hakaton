package com.hackathon.features.search;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private String tokenFor(User user) {
    return jwtTokenProvider.generateToken(user.getId(), user.getUsername());
  }

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void rejectsWithoutJwt() throws Exception {
    mvc.perform(get("/api/search").param("q", "anything")).andExpect(status().isUnauthorized());
  }

  @Test
  void emptyQueryReturnsEmptyArrays() throws Exception {
    User caller = register("caller");
    mvc.perform(get("/api/search").param("q", "   ").header("Authorization", "Bearer " + tokenFor(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms").isArray())
        .andExpect(jsonPath("$.users").isArray())
        .andExpect(jsonPath("$.rooms.length()").value(0))
        .andExpect(jsonPath("$.users.length()").value(0));
  }

  @Test
  void returnsRoomsAndUsersMatchingQuery() throws Exception {
    User caller = register("caller");
    User other = register("pickme");
    chatRoomService.createRoom("findme-" + System.nanoTime(), null, other.getId(), "public");

    String token = tokenFor(caller);
    mvc.perform(get("/api/search").param("q", "findme").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms.length()").value(greaterThanOrEqualTo(1)));

    mvc.perform(get("/api/search").param("q", "pickme").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.users.length()").value(greaterThanOrEqualTo(1)));
  }
}
