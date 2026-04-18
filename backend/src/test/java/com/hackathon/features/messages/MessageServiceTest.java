package com.hackathon.features.messages;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MessageServiceTest {
  @Autowired private MessageRepository messageRepository;
  @Autowired private MessageReactionRepository messageReactionRepository;
  @Autowired private MessageService messageService;
  @Autowired private ChatRoomService chatRoomService;
  @Autowired private UserService userService;

  private User registerUser(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  // ── existing-equivalent tests (now integration) ──────────────────────────

  @Test
  void testSendMessage() {
    User author = registerUser("send");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message result = messageService.sendMessage(room.getId(), author.getId(), "Hello world");
    assertNotNull(result);
    assertEquals("Hello world", result.getText());
  }

  @Test
  void testSendMessageNotMember() {
    User owner = registerUser("owner");
    User outsider = registerUser("outsider");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, owner.getId(), "public");
    assertThrows(IllegalArgumentException.class,
        () -> messageService.sendMessage(room.getId(), outsider.getId(), "test"));
  }

  @Test
  void testSendMessageEmpty() {
    User author = registerUser("empty");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    assertThrows(IllegalArgumentException.class,
        () -> messageService.sendMessage(room.getId(), author.getId(), ""));
  }

  @Test
  void testSendMessageTooLarge() {
    User author = registerUser("large");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    String text = "x".repeat(3073);
    assertThrows(IllegalArgumentException.class,
        () -> messageService.sendMessage(room.getId(), author.getId(), text));
  }

  @Test
  void testGetMessageHistory() {
    User author = registerUser("hist");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    messageService.sendMessage(room.getId(), author.getId(), "msg1");
    messageService.sendMessage(room.getId(), author.getId(), "msg2");
    List<Message> result = messageService.getMessageHistory(room.getId(), null, 50);
    assertEquals(2, result.size());
  }

  @Test
  void testGetMessageHistoryWithCursor() {
    User author = registerUser("cursor");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message first = messageService.sendMessage(room.getId(), author.getId(), "first");
    messageService.sendMessage(room.getId(), author.getId(), "second");
    List<Message> result = messageService.getMessageHistory(room.getId(), first.getId(), 50);
    // before cursor means messages older than first — none
    assertEquals(0, result.size());
  }

  // ── new edit/delete/reply tests ──────────────────────────────────────────

  @Test
  void editMessage_authorCanEdit_setsEditedAt() {
    User author = registerUser("editor");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "first");
    Message edited = messageService.editMessage(sent.getId(), author.getId(), "second");
    assertEquals("second", edited.getText());
    assertNotNull(edited.getEditedAt());
  }

  @Test
  void editMessage_nonAuthor_throws() {
    User author = registerUser("a");
    User other = registerUser("b");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    chatRoomService.joinRoom(room.getId(), other.getId());
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");
    assertThrows(IllegalArgumentException.class,
        () -> messageService.editMessage(sent.getId(), other.getId(), "hijacked"));
  }

  @Test
  void editMessage_afterDelete_throws() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");
    messageService.deleteMessage(sent.getId(), author.getId());
    assertThrows(IllegalArgumentException.class,
        () -> messageService.editMessage(sent.getId(), author.getId(), "re-edited"));
  }

  @Test
  void deleteMessage_authorMarksTombstone() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");
    messageService.deleteMessage(sent.getId(), author.getId());
    Message reloaded = messageRepository.findById(sent.getId()).orElseThrow();
    assertNotNull(reloaded.getDeletedAt());
    assertEquals(author.getId(), reloaded.getDeletedBy());
  }

  @Test
  void deleteMessage_isIdempotent() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");
    messageService.deleteMessage(sent.getId(), author.getId());
    OffsetDateTime firstDelete = messageRepository.findById(sent.getId()).orElseThrow().getDeletedAt();
    messageService.deleteMessage(sent.getId(), author.getId());
    OffsetDateTime secondDelete = messageRepository.findById(sent.getId()).orElseThrow().getDeletedAt();
    assertEquals(firstDelete, secondDelete);
  }

  @Test
  void deleteMessage_nonAuthor_throws() {
    User author = registerUser("a");
    User other = registerUser("b");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    chatRoomService.joinRoom(room.getId(), other.getId());
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");
    assertThrows(IllegalArgumentException.class,
        () -> messageService.deleteMessage(sent.getId(), other.getId()));
  }

  @Test
  void reply_mustTargetSameRoom() {
    User author = registerUser("a");
    ChatRoom room1 = chatRoomService.createRoom("r1-" + System.nanoTime(), null, author.getId(), "public");
    ChatRoom room2 = chatRoomService.createRoom("r2-" + System.nanoTime(), null, author.getId(), "public");
    Message inRoom1 = messageService.sendMessage(room1.getId(), author.getId(), "in1");
    assertThrows(IllegalArgumentException.class,
        () -> messageService.sendMessage(room2.getId(), author.getId(), "cross", inRoom1.getId()));
  }

  @Test
  void reply_previewShowsTruncatedSnippet() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    String longText = "x".repeat(200);
    Message parent = messageService.sendMessage(room.getId(), author.getId(), longText);
    Message reply = messageService.sendMessage(room.getId(), author.getId(), "re", parent.getId());
    ChatMessageDTO dto = messageService.toDto(reply);
    assertNotNull(dto.getReplyTo());
    assertEquals(100, dto.getReplyTo().textPreview().length());
  }

  @Test
  void reply_toDeletedParent_showsDeletedPlaceholder() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message parent = messageService.sendMessage(room.getId(), author.getId(), "orig");
    Message reply = messageService.sendMessage(room.getId(), author.getId(), "re", parent.getId());
    messageService.deleteMessage(parent.getId(), author.getId());
    ChatMessageDTO dto = messageService.toDto(reply);
    assertEquals("[deleted]", dto.getReplyTo().textPreview());
  }

  @Test
  void toDto_blanksDeletedMessageText() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "secret");
    messageService.deleteMessage(sent.getId(), author.getId());
    Message reloaded = messageRepository.findById(sent.getId()).orElseThrow();
    ChatMessageDTO dto = messageService.toDto(reloaded);
    assertNull(dto.getText());
    assertNotNull(dto.getDeletedAt());
  }

  @Test
  void toggleReaction_addsThenRemoves() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    Message sent = messageService.sendMessage(room.getId(), author.getId(), "hi");

    ChatMessageDTO afterAdd = messageService.toggleReaction(sent.getId(), author.getId(), "\uD83D\uDC4D");
    assertEquals(1, afterAdd.getReactions().size());
    assertEquals("\uD83D\uDC4D", afterAdd.getReactions().get(0).emoji());
    assertEquals(1, afterAdd.getReactions().get(0).count());
    assertTrue(afterAdd.getReactions().get(0).reactedByMe());

    ChatMessageDTO afterRemove = messageService.toggleReaction(sent.getId(), author.getId(), "\uD83D\uDC4D");
    assertTrue(afterRemove.getReactions().isEmpty());
  }

  @Test
  void toggleReaction_multipleUsersAndEmojis() {
    User a = registerUser("a");
    User b = registerUser("b");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, a.getId(), "public");
    chatRoomService.joinRoom(room.getId(), b.getId());
    Message sent = messageService.sendMessage(room.getId(), a.getId(), "hi");

    messageService.toggleReaction(sent.getId(), a.getId(), "\uD83D\uDC4D");
    messageService.toggleReaction(sent.getId(), b.getId(), "\uD83D\uDC4D");
    ChatMessageDTO dto = messageService.toggleReaction(sent.getId(), b.getId(), "\u2764\uFE0F");

    // 👍 has 2 (A + B), ❤️ has 1 (B)
    var thumbs = dto.getReactions().stream().filter(r -> "\uD83D\uDC4D".equals(r.emoji())).findFirst().orElseThrow();
    assertEquals(2, thumbs.count());
    var heart = dto.getReactions().stream().filter(r -> "\u2764\uFE0F".equals(r.emoji())).findFirst().orElseThrow();
    assertEquals(1, heart.count());
    assertTrue(heart.reactedByMe()); // caller was B; B just added heart
  }
}
