package com.hackathon.features.messages;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.TestSecurityConfig;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
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
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class MessageControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockBean private MessageService messageService;
  @MockBean private UserService userService;

  @BeforeEach
  void setUp() {
    UUID testUserId = UUID.randomUUID();
    when(userService.getUserByUsername("user"))
        .thenReturn(User.builder().id(testUserId).username("user").build());
  }

  @Test
  @WithMockUser
  void testGetMessageHistory() throws Exception {
    UUID roomId = UUID.randomUUID();
    Message msg = new Message();
    msg.setId(UUID.randomUUID());
    msg.setText("msg1");
    msg.setCreatedAt(OffsetDateTime.now());

    when(messageService.getMessageHistory(roomId, null, 50)).thenReturn(List.of(msg));

    mockMvc.perform(get("/api/rooms/{id}/messages", roomId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("msg1"));
  }

  @Test
  @WithMockUser
  void testSendMessage() throws Exception {
    UUID roomId = UUID.randomUUID();
    Message message = new Message();
    message.setId(UUID.randomUUID());
    message.setRoomId(roomId);
    message.setText("Hello");

    when(messageService.sendMessage(eq(roomId), any(UUID.class), eq("Hello")))
        .thenReturn(message);

    mockMvc.perform(post("/api/rooms/{id}/messages", roomId)
        .with(csrf())
        .contentType("application/json")
        .content("{\"text\":\"Hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Hello"));
  }
}
