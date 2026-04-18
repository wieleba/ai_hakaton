package com.hackathon.features.rooms;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.TestSecurityConfig;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ChatRoomControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private ChatRoomService chatRoomService;
  @MockBean private UserService userService;

  @BeforeEach
  void setUp() {
    UUID testUserId = UUID.randomUUID();
    when(userService.getUserByUsername("user"))
        .thenReturn(User.builder().id(testUserId).username("user").build());
    when(userService.getUserByUsername("testuser"))
        .thenReturn(User.builder().id(testUserId).username("testuser").build());
  }

  @Test
  @WithMockUser(username = "testuser")
  void testCreateRoom() throws Exception {
    UUID roomId = UUID.randomUUID();
    ChatRoom room = new ChatRoom();
    room.setId(roomId);
    room.setName("test-room");
    room.setOwnerId(UUID.randomUUID());
    room.setVisibility("public");

    when(chatRoomService.createRoom(eq("test-room"), isNull(), any(UUID.class))).thenReturn(room);

    mockMvc.perform(post("/api/rooms")
        .with(csrf())
        .contentType("application/json")
        .content("{\"name\":\"test-room\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("test-room"));
  }

  @Test
  @WithMockUser
  void testListPublicRooms() throws Exception {
    ChatRoom room = new ChatRoom();
    room.setName("room1");
    Page<ChatRoom> rooms = new PageImpl<>(List.of(room), PageRequest.of(0, 20), 1);
    when(chatRoomService.listPublicRooms(0, 20)).thenReturn(rooms);

    mockMvc.perform(get("/api/rooms?page=0&limit=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("room1"));
  }

  @Test
  @WithMockUser
  void testJoinRoom() throws Exception {
    UUID roomId = UUID.randomUUID();
    doNothing().when(chatRoomService).joinRoom(eq(roomId), any(UUID.class));

    mockMvc.perform(post("/api/rooms/{id}/join", roomId).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void testLeaveRoom() throws Exception {
    UUID roomId = UUID.randomUUID();
    doNothing().when(chatRoomService).leaveRoom(eq(roomId), any(UUID.class));

    mockMvc.perform(post("/api/rooms/{id}/leave", roomId).with(csrf()))
        .andExpect(status().isOk());
  }
}
