package com.hackathon.features.attachments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.security.JwtTokenProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired MessageService messageService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void getContent_memberGetsInlineImage() throws Exception {
    User alice = register("alice");
    String token = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] png = new byte[]{1, 2, 3, 4};
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "pic.png", "image/png", png.length, new java.io.ByteArrayInputStream(png));
    ChatMessageDTO dto = messageService.toDto(m);

    mvc.perform(get("/api/attachments/{id}/content", dto.getAttachment().id())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("inline")));
  }

  @Test
  void getContent_nonMember_403() throws Exception {
    User alice = register("alice");
    User intruder = register("intruder");
    String token = jwtTokenProvider.generateToken(intruder.getId(), intruder.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "private");
    byte[] png = new byte[]{1, 2, 3, 4};
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "pic.png", "image/png", png.length, new java.io.ByteArrayInputStream(png));
    ChatMessageDTO dto = messageService.toDto(m);

    mvc.perform(get("/api/attachments/{id}/content", dto.getAttachment().id())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void getContent_unknownId_404() throws Exception {
    User alice = register("alice");
    String token = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());
    mvc.perform(get("/api/attachments/{id}/content", java.util.UUID.randomUUID())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void getContent_noAuth_401() throws Exception {
    mvc.perform(get("/api/attachments/{id}/content", java.util.UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getContent_document_hasAttachmentDisposition() throws Exception {
    User alice = register("alice");
    String token = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] pdf = "%PDF-1.4 bytes".getBytes();
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "doc.pdf", "application/pdf", pdf.length, new java.io.ByteArrayInputStream(pdf));
    ChatMessageDTO dto = messageService.toDto(m);

    mvc.perform(get("/api/attachments/{id}/content", dto.getAttachment().id())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")));
  }
}
