package com.hackathon.features.friendships;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.TestSecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class FriendshipControllerTest {
  @Autowired MockMvc mvc;
  @MockBean FriendshipService friendshipService;
  @MockBean UserService userService;

  UUID meId;

  @BeforeEach
  void setUp() {
    meId = UUID.randomUUID();
    when(userService.getUserByUsername("user"))
        .thenReturn(User.builder().id(meId).username("user").build());
  }

  @Test
  @WithMockUser(username = "user")
  void sendRequest() throws Exception {
    Friendship f = Friendship.builder()
        .id(UUID.randomUUID())
        .requesterId(meId)
        .addresseeId(UUID.randomUUID())
        .status(Friendship.STATUS_PENDING)
        .build();
    when(friendshipService.sendRequest(eq(meId), eq("bob"))).thenReturn(f);

    mvc.perform(post("/api/friendships/requests")
            .with(csrf())
            .contentType("application/json")
            .content("{\"username\":\"bob\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"));
  }

  @Test
  @WithMockUser(username = "user")
  void listIncoming() throws Exception {
    when(friendshipService.listPendingIncoming(meId))
        .thenReturn(List.of(
            Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(UUID.randomUUID())
                .addresseeId(meId)
                .status(Friendship.STATUS_PENDING)
                .build()));

    mvc.perform(get("/api/friendships/requests?direction=incoming"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("pending"));
  }

  @Test
  @WithMockUser(username = "user")
  void accept() throws Exception {
    UUID reqId = UUID.randomUUID();
    Friendship accepted = Friendship.builder().id(reqId).status(Friendship.STATUS_ACCEPTED).build();
    when(friendshipService.accept(meId, reqId)).thenReturn(accepted);

    mvc.perform(post("/api/friendships/requests/{id}/accept", reqId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));
  }

  @Test
  @WithMockUser(username = "user")
  void reject() throws Exception {
    UUID reqId = UUID.randomUUID();
    mvc.perform(post("/api/friendships/requests/{id}/reject", reqId).with(csrf()))
        .andExpect(status().isOk());
    verify(friendshipService).reject(meId, reqId);
  }

  @Test
  @WithMockUser(username = "user")
  void listFriends() throws Exception {
    UUID otherId = UUID.randomUUID();
    when(friendshipService.listAccepted(meId))
        .thenReturn(List.of(
            Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(meId)
                .addresseeId(otherId)
                .status(Friendship.STATUS_ACCEPTED)
                .build()));
    when(userService.getUserById(otherId))
        .thenReturn(User.builder().id(otherId).username("bob").build());

    mvc.perform(get("/api/friendships"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(otherId.toString()))
        .andExpect(jsonPath("$[0].username").value("bob"));
  }

  @Test
  @WithMockUser(username = "user")
  void removeFriend() throws Exception {
    UUID otherId = UUID.randomUUID();
    mvc.perform(delete("/api/friendships/{id}", otherId).with(csrf()))
        .andExpect(status().isOk());
    verify(friendshipService).removeFriend(meId, otherId);
  }
}
