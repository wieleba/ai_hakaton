package com.hackathon.features.dms;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.DirectMessageDTO;
import java.io.ByteArrayInputStream;
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
  @Autowired DirectMessageReactionRepository directMessageReactionRepository;
  @Autowired DirectMessageAttachmentRepository attachmentRepository;

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

  // ── reactions ─────────────────────────────────────────────────────────────

  @Test
  void toggleReaction_addsThenRemoves_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");

    com.hackathon.shared.dto.DirectMessageDTO afterAdd =
        directMessageService.toggleReaction(sent.getId(), a.getId(), "\uD83D\uDC4D");
    assertEquals(1, afterAdd.getReactions().size());
    assertTrue(afterAdd.getReactions().get(0).reactedByMe());

    com.hackathon.shared.dto.DirectMessageDTO afterRemove =
        directMessageService.toggleReaction(sent.getId(), a.getId(), "\uD83D\uDC4D");
    assertTrue(afterRemove.getReactions().isEmpty());
  }

  @Test
  void toggleReaction_nonParticipant_throws() {
    User a = registerUser("a");
    User b = registerUser("b");
    User c = registerUser("c");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    DirectMessage sent = directMessageService.send(a.getId(), conv.getId(), "hi");
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.toggleReaction(sent.getId(), c.getId(), "\uD83D\uDC4D"));
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

  @Test
  void send_withAttachment_storesFile_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = new byte[]{1, 2, 3};
    DirectMessage m = directMessageService.send(
        a.getId(), conv.getId(), "hi",
        null, "pic.png", "image/png", bytes.length,
        new ByteArrayInputStream(bytes));
    DirectMessageDTO dto = directMessageService.toDto(m);
    assertNotNull(dto.getAttachment());
    assertEquals("pic.png", dto.getAttachment().filename());
  }

  @Test
  void send_withBadMime_rejected_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = "<svg/>".getBytes();
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(
            a.getId(), conv.getId(), null, null,
            "x.svg", "image/svg+xml", bytes.length,
            new ByteArrayInputStream(bytes)));
  }

  @Test
  void delete_withAttachment_removesRow_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = "hi".getBytes();
    DirectMessage m = directMessageService.send(
        a.getId(), conv.getId(), null, null,
        "hi.txt", "text/plain", bytes.length,
        new ByteArrayInputStream(bytes));
    assertTrue(attachmentRepository.findByDirectMessageId(m.getId()).isPresent());
    directMessageService.deleteMessage(m.getId(), a.getId());
    assertTrue(attachmentRepository.findByDirectMessageId(m.getId()).isEmpty());
  }
}
