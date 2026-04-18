package com.hackathon.features.rooms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.TestSecurityConfig;
import java.time.OffsetDateTime;
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
class RoomModerationControllerTest {
  @Autowired MockMvc mvc;
  @MockBean RoomModerationService roomModerationService;
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
  void kickMember() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(delete("/api/rooms/{r}/members/{u}", roomId, targetId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomModerationService).kick(eq(roomId), eq(meId), eq(targetId));
  }

  @Test
  @WithMockUser(username = "user")
  void promoteAdmin() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(
            post("/api/rooms/{r}/admins", roomId)
                .with(csrf())
                .contentType("application/json")
                .content("{\"userId\":\"" + targetId + "\"}"))
        .andExpect(status().isNoContent());
    verify(roomModerationService).promoteAdmin(eq(roomId), eq(meId), eq(targetId));
  }

  @Test
  @WithMockUser(username = "user")
  void demoteAdmin() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(delete("/api/rooms/{r}/admins/{u}", roomId, targetId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomModerationService).demoteAdmin(eq(roomId), eq(meId), eq(targetId));
  }

  @Test
  @WithMockUser(username = "user")
  void listBans() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID bannedId = UUID.randomUUID();
    UUID byId = UUID.randomUUID();
    when(roomModerationService.listBans(eq(roomId), eq(meId)))
        .thenReturn(
            List.of(
                RoomBan.builder()
                    .roomId(roomId)
                    .bannedUserId(bannedId)
                    .bannedById(byId)
                    .bannedAt(OffsetDateTime.now())
                    .build()));
    when(userService.getUserById(bannedId))
        .thenReturn(User.builder().id(bannedId).username("spammer").build());
    when(userService.getUserById(byId))
        .thenReturn(User.builder().id(byId).username("mod").build());

    mvc.perform(get("/api/rooms/{r}/bans", roomId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].bannedUsername").value("spammer"))
        .andExpect(jsonPath("$[0].bannedByUsername").value("mod"));
  }

  @Test
  @WithMockUser(username = "user")
  void unbanMember() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(delete("/api/rooms/{r}/bans/{u}", roomId, targetId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomModerationService).unban(eq(roomId), eq(meId), eq(targetId));
  }
}
