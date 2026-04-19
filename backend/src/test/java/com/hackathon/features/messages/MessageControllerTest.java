package com.hackathon.features.messages;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.hackathon.features.rooms.ChatRoom;
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
class MessageControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired MessageService messageService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private User registerUser(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  // ── legacy equivalent tests (now integration) ──────────────────────────

  @Test
  void testGetMessageHistory_includesUsername() throws Exception {
    User author = registerUser("hist");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    messageService.sendMessage(room.getId(), author.getId(), "msg1");

    mvc.perform(
            get("/api/rooms/{id}/messages", room.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("msg1"))
        .andExpect(jsonPath("$[0].username").value(author.getUsername()))
        .andExpect(jsonPath("$[0].userId").value(author.getId().toString()));
  }

  @Test
  void testSendMessage_includesUsername() throws Exception {
    User author = registerUser("send");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");

    mvc.perform(
            post("/api/rooms/{id}/messages", room.getId())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"text\":\"Hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Hello"))
        .andExpect(jsonPath("$.username").value(author.getUsername()));
  }

  // ── new PATCH / DELETE / replyTo tests ─────────────────────────────────

  @Test
  void editMessage_byAuthor_returnsOk() throws Exception {
    User author = registerUser("a");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");

    mvc.perform(
            patch("/api/rooms/{roomId}/messages/{id}", room.getId(), sent.getId())
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"text\":\"edited\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("edited"))
        .andExpect(jsonPath("$.editedAt").isNotEmpty());
  }

  @Test
  void editMessage_byNonAuthor_rejects() throws Exception {
    User author = registerUser("a");
    User other = registerUser("b");
    String otherToken = jwtTokenProvider.generateToken(other.getId(), other.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    chatRoomService.joinRoom(room.getId(), other.getId());
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");

    mvc.perform(
            patch("/api/rooms/{roomId}/messages/{id}", room.getId(), sent.getId())
                .header("Authorization", "Bearer " + otherToken)
                .contentType("application/json")
                .content("{\"text\":\"hijacked\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void deleteMessage_byAuthor_returns204() throws Exception {
    User author = registerUser("a");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");

    mvc.perform(
            delete("/api/rooms/{roomId}/messages/{id}", room.getId(), sent.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void history_includesTombstoneAndReplyPreview() throws Exception {
    User author = registerUser("a");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message parent = messageService.sendMessage(room.getId(), author.getId(), "orig");
    messageService.sendMessage(room.getId(), author.getId(), "re", parent.getId());
    messageService.deleteMessage(parent.getId(), author.getId());

    mvc.perform(
            get("/api/rooms/{roomId}/messages", room.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.replyTo != null)].replyTo.textPreview").value(hasItem("[deleted]")));
  }

  @Test
  void sendMessage_multipart_withImage_returnsDtoWithAttachment() throws Exception {
    User author = registerUser("a");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");

    var filePart = new MockMultipartFile(
        "file", "pic.png", "image/png", new byte[]{1, 2, 3});

    mvc.perform(
            MockMvcRequestBuilders
                .multipart("/api/rooms/{roomId}/messages", room.getId())
                .file(filePart)
                .param("text", "hello")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachment.filename").value("pic.png"))
        .andExpect(jsonPath("$.attachment.mimeType").value("image/png"));
  }

  @Test
  void sendMessage_multipart_withBadMime_returns400() throws Exception {
    User author = registerUser("a");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");

    var filePart = new MockMultipartFile(
        "file", "evil.svg", "image/svg+xml", "<svg/>".getBytes());

    mvc.perform(
            MockMvcRequestBuilders
                .multipart("/api/rooms/{roomId}/messages", room.getId())
                .file(filePart)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
  }
}
