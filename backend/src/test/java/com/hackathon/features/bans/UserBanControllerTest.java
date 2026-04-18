package com.hackathon.features.bans;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
class UserBanControllerTest {
  @Autowired MockMvc mvc;
  @MockBean UserBanService userBanService;
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
  void banUser() throws Exception {
    UUID bannedId = UUID.randomUUID();
    UserBan ban = UserBan.builder().id(UUID.randomUUID()).bannerId(meId).bannedId(bannedId).build();
    when(userBanService.ban(eq(meId), eq(bannedId))).thenReturn(ban);

    mvc.perform(post("/api/bans").with(csrf())
            .contentType("application/json")
            .content("{\"userId\":\"" + bannedId + "\"}"))
        .andExpect(status().isOk());

    verify(userBanService).ban(eq(meId), eq(bannedId));
  }

  @Test
  @WithMockUser(username = "user")
  void listBans() throws Exception {
    when(userBanService.listBans(meId)).thenReturn(List.of());
    mvc.perform(get("/api/bans")).andExpect(status().isOk());
  }
}
