package com.hackathon.features.dms;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DirectMessageServiceTest {

  @Autowired UserService userService;
  @Autowired FriendshipService friendshipService;
  @Autowired ConversationService conversationService;
  @Autowired DirectMessageService directMessageService;
  @Autowired DirectMessageRepository directMessageRepository;

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

  // ── existing send behaviour ────────────────────────────────────────────────

  @Test
  void send_persistsWhenFriendsAndNotBanned() {
    User a = registerUser("alice");
    User b = registerUser("bob");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());

    DirectMessage saved = directMessageService.send(a.getId(), conv.getId(), "hello");

    assertEquals("hello", saved.getText());
    assertEquals(a.getId(), saved.getSenderId());
    assertEquals(conv.getId(), saved.getConversationId());
  }

  @Test
  void send_rejectsNonParticipant() {
    User a = registerUser("a");
    User b = registerUser("b");
    User c = registerUser("c");
    makeFriends(a, b);
    makeFriends(a, c);
    DirectConversation ab = conversationService.getOrCreate(a.getId(), b.getId());
    // c is not a participant of ab's conversation
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(c.getId(), ab.getId(), "hi"));
  }

  @Test
  void send_rejectsWhenNotFriends() {
    User a = registerUser("a");
    User b = registerUser("b");
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(a.getId(), conv.getId(), "hi"));
  }

  @Test
  void send_rejectsEmptyText() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(a.getId(), conv.getId(), "  "));
  }

  @Test
  void send_rejectsTextOver3072() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(a.getId(), conv.getId(), "x".repeat(3073)));
  }

  @Test
  void getHistory_withoutCursor_returnsMessages() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    directMessageService.send(a.getId(), conv.getId(), "msg1");
    directMessageService.send(b.getId(), conv.getId(), "msg2");

    var history = directMessageService.getHistory(conv.getId(), null, 50);

    assertEquals(2, history.size());
  }

  @Test
  void getHistory_withCursor_returnsEmpty_atBeginning() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage first = directMessageService.send(a.getId(), conv.getId(), "first");

    // cursor at the first message → nothing before it
    var history = directMessageService.getHistory(conv.getId(), first.getId(), 50);

    assertEquals(0, history.size());
  }

  // ── edit ──────────────────────────────────────────────────────────────────

  @Test
  void editMessage_authorCanEdit_setsEditedAt_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "orig");
    DirectMessage edited = directMessageService.editMessage(sent.getId(), a.getId(), "new");
    assertEquals("new", edited.getText());
    assertNotNull(edited.getEditedAt());
  }

  @Test
  void editMessage_nonAuthor_throws_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.editMessage(sent.getId(), b.getId(), "hijacked"));
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void deleteMessage_authorMarksTombstone_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");
    directMessageService.deleteMessage(sent.getId(), a.getId());
    DirectMessage reloaded = directMessageRepository.findById(sent.getId()).orElseThrow();
    assertNotNull(reloaded.getDeletedAt());
    assertEquals(a.getId(), reloaded.getDeletedBy());
  }

  @Test
  void deleteMessage_isIdempotent_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");
    directMessageService.deleteMessage(sent.getId(), a.getId());
    OffsetDateTime first = directMessageRepository.findById(sent.getId()).orElseThrow().getDeletedAt();
    directMessageService.deleteMessage(sent.getId(), a.getId());
    OffsetDateTime second = directMessageRepository.findById(sent.getId()).orElseThrow().getDeletedAt();
    assertEquals(first, second);
  }

  // ── reply ─────────────────────────────────────────────────────────────────

  @Test
  void reply_mustTargetSameConversation() {
    User a = registerUser("a");
    User b = registerUser("b");
    User c = registerUser("c");
    makeFriends(a, b);
    makeFriends(a, c);
    DirectConversation ab = conversationService.getOrCreate(a.getId(), b.getId());
    DirectConversation ac = conversationService.getOrCreate(a.getId(), c.getId());
    DirectMessage inAB = directMessageService.send(a.getId(), ab.getId(), "in-ab");
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(a.getId(), ac.getId(), "cross", inAB.getId()));
  }
}
