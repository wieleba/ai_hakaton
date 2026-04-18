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
class RoomInvitationControllerTest {
  @Autowired MockMvc mvc;
  @MockBean RoomInvitationService roomInvitationService;
  @MockBean ChatRoomRepository chatRoomRepository;
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
  void invite() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID inviteeId = UUID.randomUUID();
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(UUID.randomUUID())
            .roomId(roomId)
            .inviterId(meId)
            .inviteeId(inviteeId)
            .createdAt(OffsetDateTime.now())
            .build();
    when(roomInvitationService.invite(eq(roomId), eq(meId), eq("bob"))).thenReturn(inv);

    mvc.perform(
            post("/api/rooms/{r}/invitations", roomId)
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"bob\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roomId").value(roomId.toString()));
  }

  @Test
  @WithMockUser(username = "user")
  void listMyIncoming_resolvesRoomAndInviterNames() throws Exception {
    UUID invId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    UUID inviterId = UUID.randomUUID();
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(meId)
            .createdAt(OffsetDateTime.now())
            .build();
    when(roomInvitationService.listMyIncoming(meId)).thenReturn(List.of(inv));
    ChatRoom room = new ChatRoom();
    room.setId(roomId);
    room.setName("Sekrit");
    when(chatRoomRepository.findById(roomId)).thenReturn(java.util.Optional.of(room));
    when(userService.getUserById(inviterId))
        .thenReturn(User.builder().id(inviterId).username("alice").build());

    mvc.perform(get("/api/invitations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].roomName").value("Sekrit"))
        .andExpect(jsonPath("$[0].inviterUsername").value("alice"));
  }

  @Test
  @WithMockUser(username = "user")
  void accept() throws Exception {
    UUID invId = UUID.randomUUID();
    mvc.perform(post("/api/invitations/{id}/accept", invId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomInvitationService).accept(eq(invId), eq(meId));
  }

  @Test
  @WithMockUser(username = "user")
  void decline() throws Exception {
    UUID invId = UUID.randomUUID();
    mvc.perform(post("/api/invitations/{id}/decline", invId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomInvitationService).decline(eq(invId), eq(meId));
  }

  @Test
  @WithMockUser(username = "user")
  void cancel() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID invId = UUID.randomUUID();
    mvc.perform(delete("/api/rooms/{r}/invitations/{i}", roomId, invId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomInvitationService).cancel(eq(invId), eq(meId));
  }
}
