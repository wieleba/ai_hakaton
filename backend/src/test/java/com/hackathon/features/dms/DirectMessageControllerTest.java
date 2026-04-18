package com.hackathon.features.dms;

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
import java.util.Optional;
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
class DirectMessageControllerTest {
  @Autowired MockMvc mvc;
  @MockBean DirectMessageService directMessageService;
  @MockBean ConversationService conversationService;
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
  void listConversations() throws Exception {
    UUID otherId = UUID.randomUUID();
    DirectConversation conv =
        DirectConversation.builder().id(UUID.randomUUID()).user1Id(meId).user2Id(otherId).build();
    when(directMessageService.listConversations(meId)).thenReturn(List.of(conv));
    when(conversationService.otherParticipant(conv, meId)).thenReturn(otherId);
    when(directMessageService.lastMessage(conv.getId()))
        .thenReturn(
            Optional.of(
                DirectMessage.builder()
                    .id(UUID.randomUUID())
                    .text("hey")
                    .senderId(otherId)
                    .build()));
    when(userService.getUserById(otherId))
        .thenReturn(User.builder().id(otherId).username("bob").build());

    mvc.perform(get("/api/dms/conversations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].otherUsername").value("bob"))
        .andExpect(jsonPath("$[0].lastMessage").value("hey"));
  }

  @Test
  @WithMockUser(username = "user")
  void getOrCreateWithOther() throws Exception {
    UUID otherId = UUID.randomUUID();
    UUID convId = UUID.randomUUID();
    when(conversationService.getOrCreate(eq(meId), eq(otherId)))
        .thenReturn(
            DirectConversation.builder().id(convId).user1Id(meId).user2Id(otherId).build());

    mvc.perform(get("/api/dms/with/{otherId}", otherId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(convId.toString()));
  }

  @Test
  @WithMockUser(username = "user")
  void getHistory() throws Exception {
    UUID convId = UUID.randomUUID();
    when(directMessageService.getHistory(convId, null, 50))
        .thenReturn(
            List.of(
                DirectMessage.builder()
                    .id(UUID.randomUUID())
                    .text("a")
                    .senderId(meId)
                    .build()));

    mvc.perform(get("/api/dms/{id}/messages", convId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("a"));
  }

  @Test
  @WithMockUser(username = "user")
  void sendViaRest() throws Exception {
    UUID convId = UUID.randomUUID();
    DirectMessage saved =
        DirectMessage.builder()
            .id(UUID.randomUUID())
            .conversationId(convId)
            .senderId(meId)
            .text("hi")
            .build();
    when(directMessageService.send(meId, convId, "hi")).thenReturn(saved);

    mvc.perform(
            post("/api/dms/{id}/messages", convId)
                .with(csrf())
                .contentType("application/json")
                .content("{\"text\":\"hi\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("hi"));
  }
}
