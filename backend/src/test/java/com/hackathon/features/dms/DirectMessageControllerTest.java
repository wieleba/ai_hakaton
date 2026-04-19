package com.hackathon.features.dms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
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
class DirectMessageControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired FriendshipService friendshipService;
  @Autowired ConversationService conversationService;
  @Autowired DirectMessageService directMessageService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private User registerUser(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  private void makeFriends(User a, User b) {
    Friendship req = friendshipService.sendRequest(a.getId(), b.getUsername());
    friendshipService.accept(b.getId(), req.getId());
  }

  @Test
  void listConversations() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    directMessageService.send(a.getId(), conv.getId(), "hey");
    String tokenA = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    mvc.perform(
            get("/api/dms/conversations")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].otherUsername").value(b.getUsername()))
        .andExpect(jsonPath("$[0].lastMessage").value("hey"));
  }

  @Test
  void getOrCreateWithOther() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    String tokenA = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    mvc.perform(
            get("/api/dms/with/{otherId}", b.getId())
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user1Id").exists());
  }

  @Test
  void getHistory() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    directMessageService.send(a.getId(), conv.getId(), "a-msg");
    String tokenA = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    mvc.perform(
            get("/api/dms/{id}/messages", conv.getId())
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("a-msg"));
  }

  @Test
  void sendViaRest() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    String tokenA = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    mvc.perform(
            post("/api/dms/{id}/messages", conv.getId())
                .header("Authorization", "Bearer " + tokenA)
                .contentType("application/json")
                .content("{\"text\":\"hi\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("hi"));
  }

  @Test
  void editMessage_byAuthor_returnsOk_dm() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");
    String tokenA = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    mvc.perform(
            patch("/api/dms/{cid}/messages/{id}", conv.getId(), sent.getId())
                .header("Authorization", "Bearer " + tokenA)
                .contentType("application/json")
                .content("{\"text\":\"edited\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("edited"))
        .andExpect(jsonPath("$.editedAt").isNotEmpty());
  }

  @Test
  void deleteMessage_byAuthor_returns204_dm() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");
    String tokenA = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    mvc.perform(
            delete("/api/dms/{cid}/messages/{id}", conv.getId(), sent.getId())
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNoContent());
  }

  @Test
  void editMessage_byNonAuthor_rejects_dm() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");
    String tokenB = jwtTokenProvider.generateToken(b.getId(), b.getUsername());

    mvc.perform(
            patch("/api/dms/{cid}/messages/{id}", conv.getId(), sent.getId())
                .header("Authorization", "Bearer " + tokenB)
                .contentType("application/json")
                .content("{\"text\":\"hijacked\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void sendMessage_multipart_withImage_returnsDtoWithAttachment_dm() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    String token = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    var filePart = new MockMultipartFile(
        "file", "pic.png", "image/png", new byte[]{1, 2, 3});

    mvc.perform(
            MockMvcRequestBuilders
                .multipart("/api/dms/{cid}/messages", conv.getId())
                .file(filePart)
                .param("text", "hi")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachment.filename").value("pic.png"));
  }
}
