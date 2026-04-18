# Message Content Enhancements (Reply + Edit + Delete) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reply-to-message, author edit with visible indicator, and author soft-delete to both room messages and direct messages, with real-time propagation over WebSocket.

**Architecture:** V5 Flyway migration adds `edited_at`, `deleted_at`, `deleted_by`, `reply_to_id` to `messages` and `direct_messages`. Backend gains PATCH/DELETE on both controllers and extends WS payloads to a tagged union `{type: CREATED|EDITED|DELETED, message}`. Frontend extracts `MessageItem`, adds a reply pill in the existing `ComposerActions` slot, an inline editor, and branches on the WS event `type`.

**Tech Stack:** Spring Boot 3.5.12, Java 25, PostgreSQL 15, Flyway, JPA/Hibernate, JUnit 5, MockMvc, Spring WebSocket + STOMP; React 19, TypeScript, Vite, @stomp/stompjs, axios, Playwright.

**Spec:** `docs/superpowers/specs/2026-04-18-message-content-design.md`

---

## File Structure

### Backend

```
backend/src/main/resources/db/migration/
  V5__message_content.sql                                                          (new)

backend/src/main/java/com/hackathon/features/messages/
  Message.java                                                                     (+4 columns)
  MessageService.java                                                              (+edit, +delete, +replyToId)
  MessageController.java                                                           (+PATCH, +DELETE, ChatMessageDTO upgrades)
  MessageRepository.java                                                           (no changes)

backend/src/main/java/com/hackathon/features/dms/
  DirectMessage.java                                                               (+4 columns)
  DirectMessageService.java                                                        (+edit, +delete, +replyToId)
  DirectMessageController.java                                                     (+PATCH, +DELETE, returns DirectMessageDTO)
  DirectMessageDTO.java                                                            (new)

backend/src/main/java/com/hackathon/shared/dto/
  ChatMessageDTO.java                                                              (+editedAt, deletedAt, deletedBy, replyTo)
  MessagePreview.java                                                              (new — reply preview record)
  MessageEventEnvelope.java                                                        (new — tagged union)
  DirectMessageEventEnvelope.java                                                  (new — tagged union)

backend/src/main/java/com/hackathon/shared/websocket/
  ChatMessageHandler.java                                                          (publishes CREATED envelope; edit/delete events published from MessageService)
  DirectMessageHandler.java                                                        (same)

backend/src/test/java/com/hackathon/features/messages/
  MessageServiceTest.java                                                          (extend — edit/delete/reply)
  MessageControllerTest.java                                                       (extend — PATCH/DELETE)

backend/src/test/java/com/hackathon/features/dms/
  DirectMessageServiceTest.java                                                    (extend)
  DirectMessageControllerTest.java                                                 (extend)

backend/src/test/java/com/hackathon/features/integration/
  MessageContentFlowIntegrationTest.java                                           (new)
```

### Frontend

```
frontend/src/types/
  room.ts                                                                          (+editedAt, deletedAt, deletedBy, replyTo on Message)
  directMessage.ts                                                                 (+ same on DirectMessage)

frontend/src/services/
  messageService.ts                                                                (+editMessage, deleteMessage, sendMessage with replyToId)
  directMessageService.ts                                                          (+editMessage, deleteMessage, sendMessage with replyToId)

frontend/src/hooks/
  useRoomMessages.ts                                                               (branch on event type; upsert + markDeleted helpers)
  useDirectMessages.ts                                                             (same)
  useWebSocket.ts                                                                  (sendMessage accepts replyToId)
  useDirectMessageSocket.ts                                                        (sendDm accepts replyToId)

frontend/src/components/
  MessageList.tsx                                                                  (delegates to MessageItem per row; passes action callbacks)
  MessageItem.tsx                                                                  (new — single message row with hover actions, reply header, tombstone, edit mode)
  MessageActionsMenu.tsx                                                           (new — floating hover bar: Reply/Edit/Delete)
  ReplyPill.tsx                                                                    (new — composer reply chip)
  InlineMessageEditor.tsx                                                          (new — inline textarea with Save/Cancel)

frontend/src/pages/
  ChatPage.tsx                                                                     (replyTarget state; edit/delete handlers)
  DirectChatPage.tsx                                                               (same)

frontend/e2e/
  message-content.spec.ts                                                          (new)

FEATURES_ROADMAP.md                                                                (Feature #5 content marked complete)
```

---

## Implementation Tasks

### Task 1: V5 Flyway migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__message_content.sql`

- [ ] **Step 1: Create the migration**

```sql
ALTER TABLE messages
  ADD COLUMN edited_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_by UUID NULL REFERENCES users(id),
  ADD COLUMN reply_to_id UUID NULL REFERENCES messages(id) ON DELETE SET NULL;

ALTER TABLE direct_messages
  ADD COLUMN edited_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_by UUID NULL REFERENCES users(id),
  ADD COLUMN reply_to_id UUID NULL REFERENCES direct_messages(id) ON DELETE SET NULL;

CREATE INDEX idx_messages_reply_to ON messages(reply_to_id);
CREATE INDEX idx_direct_messages_reply_to ON direct_messages(reply_to_id);
```

- [ ] **Step 2: Verify compile**

`cd /src/ai_hakaton/backend && ./gradlew compileJava compileTestJava` — expect clean.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/resources/db/migration/V5__message_content.sql
git commit -m "database: V5 migration — message edit/delete/reply columns" -m "Both messages and direct_messages gain edited_at, deleted_at, deleted_by, reply_to_id. reply_to_id uses ON DELETE SET NULL so cascades from room deletion don't leave dangling FKs." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Entity updates (Message, DirectMessage) + DTO primitives

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/messages/Message.java`
- Modify: `backend/src/main/java/com/hackathon/features/dms/DirectMessage.java`
- Create: `backend/src/main/java/com/hackathon/shared/dto/MessagePreview.java`
- Modify: `backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java`
- Create: `backend/src/main/java/com/hackathon/shared/dto/DirectMessageDTO.java`
- Create: `backend/src/main/java/com/hackathon/shared/dto/MessageEventEnvelope.java`
- Create: `backend/src/main/java/com/hackathon/shared/dto/DirectMessageEventEnvelope.java`

This task adds only types so every subsequent task builds on a stable shape.

- [ ] **Step 1: Update `Message.java`**

Replace entire file contents:

```java
package com.hackathon.features.messages;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 3072)
  private String text;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "edited_at")
  private OffsetDateTime editedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;

  @Column(name = "reply_to_id")
  private UUID replyToId;
}
```

- [ ] **Step 2: Update `DirectMessage.java`**

Replace entire file contents:

```java
package com.hackathon.features.dms;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "direct_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "sender_id", nullable = false)
  private UUID senderId;

  @Column(nullable = false, length = 3072)
  private String text;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "edited_at")
  private OffsetDateTime editedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;

  @Column(name = "reply_to_id")
  private UUID replyToId;
}
```

- [ ] **Step 3: Create `MessagePreview.java`**

```java
package com.hackathon.shared.dto;

import java.util.UUID;

public record MessagePreview(UUID id, String authorUsername, String textPreview) {
  public static final int PREVIEW_CHARS = 100;
  public static final String DELETED_PLACEHOLDER = "[deleted]";
}
```

- [ ] **Step 4: Replace `ChatMessageDTO.java`**

```java
package com.hackathon.shared.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
  private UUID id;
  private UUID roomId;
  private UUID userId;
  private String username;
  private String text;
  private OffsetDateTime createdAt;
  private OffsetDateTime editedAt;
  private OffsetDateTime deletedAt;
  private UUID deletedBy;
  private MessagePreview replyTo;
}
```

- [ ] **Step 5: Create `DirectMessageDTO.java`**

```java
package com.hackathon.shared.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageDTO {
  private UUID id;
  private UUID conversationId;
  private UUID senderId;
  private String senderUsername;
  private String text;
  private OffsetDateTime createdAt;
  private OffsetDateTime editedAt;
  private OffsetDateTime deletedAt;
  private UUID deletedBy;
  private MessagePreview replyTo;
}
```

- [ ] **Step 6: Create `MessageEventEnvelope.java`**

```java
package com.hackathon.shared.dto;

/** Tagged-union envelope sent over /topic/room/{roomId}. */
public record MessageEventEnvelope(Type type, ChatMessageDTO message) {
  public enum Type {
    CREATED,
    EDITED,
    DELETED
  }

  public static MessageEventEnvelope created(ChatMessageDTO m) {
    return new MessageEventEnvelope(Type.CREATED, m);
  }

  public static MessageEventEnvelope edited(ChatMessageDTO m) {
    return new MessageEventEnvelope(Type.EDITED, m);
  }

  public static MessageEventEnvelope deleted(ChatMessageDTO m) {
    return new MessageEventEnvelope(Type.DELETED, m);
  }
}
```

- [ ] **Step 7: Create `DirectMessageEventEnvelope.java`**

```java
package com.hackathon.shared.dto;

/** Tagged-union envelope sent to /user/{uuid}/queue/dms. */
public record DirectMessageEventEnvelope(Type type, DirectMessageDTO message) {
  public enum Type {
    CREATED,
    EDITED,
    DELETED
  }

  public static DirectMessageEventEnvelope created(DirectMessageDTO m) {
    return new DirectMessageEventEnvelope(Type.CREATED, m);
  }

  public static DirectMessageEventEnvelope edited(DirectMessageDTO m) {
    return new DirectMessageEventEnvelope(Type.EDITED, m);
  }

  public static DirectMessageEventEnvelope deleted(DirectMessageDTO m) {
    return new DirectMessageEventEnvelope(Type.DELETED, m);
  }
}
```

- [ ] **Step 8: Build + run existing tests**

`cd /src/ai_hakaton/backend && ./gradlew compileJava compileTestJava`
Expected: clean compile. No functional changes yet so behaviour identical.

- [ ] **Step 9: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/messages/Message.java \
        backend/src/main/java/com/hackathon/features/dms/DirectMessage.java \
        backend/src/main/java/com/hackathon/shared/dto
git commit -m "feat(messages): entity columns + DTO upgrades for edit/delete/reply" -m "- Message + DirectMessage gain editedAt, deletedAt, deletedBy, replyToId" -m "- ChatMessageDTO adds the same (+ replyTo MessagePreview record)" -m "- New DirectMessageDTO mirrors ChatMessageDTO" -m "- New tagged MessageEventEnvelope / DirectMessageEventEnvelope for WS payloads" -m "- No behaviour change yet; services/controllers still populate only the pre-existing fields" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Room `MessageService` — edit, delete, reply + WS emission + tests

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageService.java`
- Modify: `backend/src/test/java/com/hackathon/features/messages/MessageServiceTest.java`

- [ ] **Step 1: Look up existing service tests pattern**

Run: `cat backend/src/test/java/com/hackathon/features/messages/MessageServiceTest.java | head -40` to confirm SpringBootTest + registerUser pattern matches the integration-style tests used elsewhere.

- [ ] **Step 2: Rewrite `MessageService.java`**

```java
package com.hackathon.features.messages;

import com.hackathon.features.rooms.RoomMemberService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.dto.MessageEventEnvelope;
import com.hackathon.shared.dto.MessagePreview;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {
  private static final int MAX_MESSAGE_SIZE = 3072;

  private final MessageRepository messageRepository;
  private final RoomMemberService roomMemberService;
  private final UserService userService;
  private final SimpMessagingTemplate messagingTemplate;

  @Transactional
  public Message sendMessage(UUID roomId, UUID userId, String text, UUID replyToId) {
    if (!roomMemberService.isMember(roomId, userId)) {
      throw new IllegalArgumentException("User is not a member of this room");
    }
    validateText(text);
    if (replyToId != null) {
      Message parent = messageRepository.findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getRoomId().equals(roomId)) {
        throw new IllegalArgumentException("Reply target is in a different room");
      }
    }
    Message saved = messageRepository.save(
        Message.builder()
            .roomId(roomId)
            .userId(userId)
            .text(text)
            .replyToId(replyToId)
            .build());
    publish(MessageEventEnvelope.created(toDto(saved)));
    return saved;
  }

  /** Overload used by callers that never reply (tests, internal). */
  public Message sendMessage(UUID roomId, UUID userId, String text) {
    return sendMessage(roomId, userId, text, null);
  }

  @Transactional
  public Message editMessage(UUID messageId, UUID callerId, String newText) {
    Message m = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getUserId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can edit this message");
    }
    if (m.getDeletedAt() != null) {
      throw new IllegalArgumentException("Cannot edit a deleted message");
    }
    validateText(newText);
    m.setText(newText);
    m.setEditedAt(OffsetDateTime.now());
    Message saved = messageRepository.save(m);
    publish(MessageEventEnvelope.edited(toDto(saved)));
    return saved;
  }

  @Transactional
  public void deleteMessage(UUID messageId, UUID callerId) {
    Message m = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getUserId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can delete this message");
    }
    if (m.getDeletedAt() != null) {
      return; // idempotent
    }
    m.setDeletedAt(OffsetDateTime.now());
    m.setDeletedBy(callerId);
    Message saved = messageRepository.save(m);
    publish(MessageEventEnvelope.deleted(toDto(saved)));
  }

  public List<Message> getMessageHistory(UUID roomId, UUID beforeMessageId, int limit) {
    if (beforeMessageId == null) {
      return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, limit));
    } else {
      return messageRepository.findByRoomIdBeforeCursor(roomId, beforeMessageId, PageRequest.of(0, limit));
    }
  }

  public long getMessageCount(UUID roomId) {
    return messageRepository.countByRoomId(roomId);
  }

  /** Build the outbound DTO, blanking text on tombstones and resolving the reply preview. */
  public ChatMessageDTO toDto(Message m) {
    String displayedText = m.getDeletedAt() == null ? m.getText() : null;
    String username = resolveUsername(m.getUserId());
    MessagePreview preview = null;
    if (m.getReplyToId() != null) {
      Optional<Message> parent = messageRepository.findById(m.getReplyToId());
      if (parent.isPresent()) {
        Message p = parent.get();
        String snippet;
        if (p.getDeletedAt() != null) {
          snippet = MessagePreview.DELETED_PLACEHOLDER;
        } else {
          String t = p.getText() == null ? "" : p.getText();
          snippet = t.length() > MessagePreview.PREVIEW_CHARS
              ? t.substring(0, MessagePreview.PREVIEW_CHARS)
              : t;
        }
        preview = new MessagePreview(p.getId(), resolveUsername(p.getUserId()), snippet);
      }
    }
    return ChatMessageDTO.builder()
        .id(m.getId())
        .roomId(m.getRoomId())
        .userId(m.getUserId())
        .username(username)
        .text(displayedText)
        .createdAt(m.getCreatedAt())
        .editedAt(m.getEditedAt())
        .deletedAt(m.getDeletedAt())
        .deletedBy(m.getDeletedBy())
        .replyTo(preview)
        .build();
  }

  private void validateText(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Message text cannot be empty");
    }
    if (text.length() > MAX_MESSAGE_SIZE) {
      throw new IllegalArgumentException(
          "Message exceeds maximum size of " + MAX_MESSAGE_SIZE + " bytes");
    }
  }

  private void publish(MessageEventEnvelope env) {
    messagingTemplate.convertAndSend("/topic/room/" + env.message().getRoomId(), env);
  }

  private String resolveUsername(UUID userId) {
    try {
      User u = userService.getUserById(userId);
      return u.getUsername();
    } catch (IllegalArgumentException e) {
      return userId.toString().substring(0, 8);
    }
  }
}
```

- [ ] **Step 3: Extend `MessageServiceTest.java`** — add these tests in addition to existing ones

Open `backend/src/test/java/com/hackathon/features/messages/MessageServiceTest.java` and append inside the class. (Do not remove existing tests.) Keep existing imports and add:

```java
import com.hackathon.shared.dto.ChatMessageDTO;
```

Append these test methods to the class body:

```java
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
```

Ensure `messageRepository`, `messageService`, `chatRoomService`, `registerUser(...)` are already autowired in the test class. If any aren't, add `@Autowired MessageRepository messageRepository;` and the `private User registerUser(String suffix)` helper (same pattern as `RoomModerationFlowIntegrationTest`).

If the test file is minimal, expand to the same pattern as `FriendsAndDmsFlowIntegrationTest`: `@SpringBootTest @ActiveProfiles("test") class MessageServiceTest { @Autowired ... ; private User registerUser(String s) { ... } }` and include the new tests plus any pre-existing coverage.

- [ ] **Step 4: Run tests**

`cd /src/ai_hakaton/backend && ./gradlew test --tests 'MessageServiceTest'`

Expected: all MessageServiceTest cases pass (existing + 10 new).

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/messages/MessageService.java \
        backend/src/test/java/com/hackathon/features/messages/MessageServiceTest.java
git commit -m "feat(messages): room message edit/delete/reply service + WS emission" -m "- sendMessage(roomId, userId, text, replyToId) validates same-room reply target" -m "- editMessage: author-only, blocked on tombstone, sets editedAt" -m "- deleteMessage: author-only, idempotent, soft-delete with deletedBy" -m "- toDto blanks text on tombstones and resolves 100-char preview (or [deleted] placeholder)" -m "- Publishes CREATED/EDITED/DELETED envelopes on /topic/room/{roomId}" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Room `MessageController` — PATCH + DELETE + replyToId on POST

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageController.java`
- Modify: `backend/src/main/java/com/hackathon/shared/websocket/ChatMessageHandler.java`
- Modify: `backend/src/test/java/com/hackathon/features/messages/MessageControllerTest.java`

The REST controller adds `PATCH` and `DELETE` endpoints, surfaces the upgraded DTO on history + POST, and calls the new `MessageService.toDto(...)` so we don't duplicate preview-building logic. The WS handler switches to `MessageEventEnvelope` so the `@SendTo` emits the tagged union for `CREATED`. `EDITED`/`DELETED` events come from `MessageService` via `SimpMessagingTemplate` (already done in Task 3).

- [ ] **Step 1: Replace `MessageController.java`**

```java
package com.hackathon.features.messages;

import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {
  private final MessageService messageService;
  private final UserService userService;

  record SendMessageRequest(String text, UUID replyToId) {}

  record EditMessageRequest(String text) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) {
      return uuid;
    }
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping
  public ResponseEntity<List<ChatMessageDTO>> getMessageHistory(
      @PathVariable UUID roomId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    List<Message> messages = messageService.getMessageHistory(roomId, before, limit);
    List<ChatMessageDTO> views = messages.stream().map(messageService::toDto).toList();
    return ResponseEntity.ok(views);
  }

  @PostMapping
  public ResponseEntity<ChatMessageDTO> sendMessage(
      @PathVariable UUID roomId,
      @RequestBody SendMessageRequest request,
      Authentication authentication) {
    UUID userId = currentUserId(authentication);
    Message message =
        messageService.sendMessage(roomId, userId, request.text(), request.replyToId());
    return ResponseEntity.ok(messageService.toDto(message));
  }

  @PatchMapping("/{messageId}")
  public ResponseEntity<ChatMessageDTO> editMessage(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      @RequestBody EditMessageRequest request,
      Authentication authentication) {
    UUID userId = currentUserId(authentication);
    Message edited = messageService.editMessage(messageId, userId, request.text());
    return ResponseEntity.ok(messageService.toDto(edited));
  }

  @DeleteMapping("/{messageId}")
  public ResponseEntity<Void> deleteMessage(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      Authentication authentication) {
    UUID userId = currentUserId(authentication);
    messageService.deleteMessage(messageId, userId);
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 2: Replace `ChatMessageHandler.java`**

```java
package com.hackathon.shared.websocket;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.shared.dto.MessageEventEnvelope;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {
  private final MessageService messageService;

  public record RoomSendPayload(String text, UUID replyToId) {}

  @MessageMapping("/rooms/{roomId}/message")
  public void handleMessage(
      RoomSendPayload payload, @DestinationVariable UUID roomId, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    // Service publishes the CREATED envelope on /topic/room/{roomId} itself.
    messageService.sendMessage(roomId, userId, payload.text(), payload.replyToId());
  }
}
```

Note: the old handler returned `ChatMessageDTO` with `@SendTo("/topic/room/{roomId}")`. We now rely on `MessageService` to publish via `SimpMessagingTemplate`, so no `@SendTo` is needed.

- [ ] **Step 3: Mapping in existing `MessageControllerTest.java`**

Read the file first. Keep its existing tests. Add the following imports if not present:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

Append these test methods (adapting user-registration + token helper if already defined):

```java
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
        .andExpect(jsonPath("$[?(@.replyTo != null)].replyTo.textPreview").value(org.hamcrest.Matchers.hasItem("[deleted]")));
  }
```

Ensure the class injects: `@Autowired MockMvc mvc; @Autowired UserService userService; @Autowired ChatRoomService chatRoomService; @Autowired MessageService messageService; @Autowired JwtTokenProvider jwtTokenProvider;` and has a `registerUser(String suffix)` helper.

- [ ] **Step 4: Run controller tests**

`cd /src/ai_hakaton/backend && ./gradlew test --tests 'MessageControllerTest'`

Expected: PASS (existing + 4 new).

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/messages/MessageController.java \
        backend/src/main/java/com/hackathon/shared/websocket/ChatMessageHandler.java \
        backend/src/test/java/com/hackathon/features/messages/MessageControllerTest.java
git commit -m "feat(messages): REST PATCH + DELETE + replyToId; WS handler uses envelope" -m "- Controller delegates DTO assembly to MessageService.toDto (single source of truth for previews)" -m "- PATCH /api/rooms/{roomId}/messages/{id} with {text}" -m "- DELETE /api/rooms/{roomId}/messages/{id}" -m "- POST body gains optional replyToId" -m "- ChatMessageHandler drops @SendTo; MessageService publishes the CREATED envelope itself" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: DM `DirectMessageService` — edit, delete, reply + WS emission + tests

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java`
- Modify: `backend/src/test/java/com/hackathon/features/dms/DirectMessageServiceTest.java`

- [ ] **Step 1: Replace `DirectMessageService.java`**

```java
package com.hackathon.features.dms;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.DirectMessageDTO;
import com.hackathon.shared.dto.DirectMessageEventEnvelope;
import com.hackathon.shared.dto.MessagePreview;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {
  private static final int MAX_TEXT = 3072;

  private final DirectMessageRepository directMessageRepository;
  private final DirectConversationRepository directConversationRepository;
  private final ConversationService conversationService;
  private final FriendshipRepository friendshipRepository;
  private final UserBanRepository userBanRepository;
  private final UserService userService;
  private final SimpMessagingTemplate messagingTemplate;

  @Transactional
  public DirectMessage send(UUID senderId, UUID conversationId, String text, UUID replyToId) {
    DirectConversation conv = loadConversation(conversationId);
    if (!conv.getUser1Id().equals(senderId) && !conv.getUser2Id().equals(senderId)) {
      throw new IllegalArgumentException("Not a participant of this conversation");
    }
    UUID other = conversationService.otherParticipant(conv, senderId);
    ensureFriendsAndNotBanned(senderId, other);
    validateText(text);
    if (replyToId != null) {
      DirectMessage parent = directMessageRepository
          .findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getConversationId().equals(conversationId)) {
        throw new IllegalArgumentException("Reply target is in a different conversation");
      }
    }
    DirectMessage saved = directMessageRepository.save(
        DirectMessage.builder()
            .conversationId(conversationId)
            .senderId(senderId)
            .text(text)
            .replyToId(replyToId)
            .build());
    publishToBoth(senderId, other, DirectMessageEventEnvelope.created(toDto(saved)));
    return saved;
  }

  public DirectMessage send(UUID senderId, UUID conversationId, String text) {
    return send(senderId, conversationId, text, null);
  }

  @Transactional
  public DirectMessage sendToUser(UUID senderId, UUID recipientId, String text) {
    ensureFriendsAndNotBanned(senderId, recipientId);
    DirectConversation conv = conversationService.getOrCreate(senderId, recipientId);
    return send(senderId, conv.getId(), text, null);
  }

  @Transactional
  public DirectMessage editMessage(UUID messageId, UUID callerId, String newText) {
    DirectMessage m = directMessageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getSenderId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can edit this message");
    }
    if (m.getDeletedAt() != null) {
      throw new IllegalArgumentException("Cannot edit a deleted message");
    }
    validateText(newText);
    m.setText(newText);
    m.setEditedAt(OffsetDateTime.now());
    DirectMessage saved = directMessageRepository.save(m);
    DirectConversation conv = loadConversation(saved.getConversationId());
    UUID other = conversationService.otherParticipant(conv, callerId);
    publishToBoth(callerId, other, DirectMessageEventEnvelope.edited(toDto(saved)));
    return saved;
  }

  @Transactional
  public void deleteMessage(UUID messageId, UUID callerId) {
    DirectMessage m = directMessageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    if (!m.getSenderId().equals(callerId)) {
      throw new IllegalArgumentException("Only the author can delete this message");
    }
    if (m.getDeletedAt() != null) {
      return;
    }
    m.setDeletedAt(OffsetDateTime.now());
    m.setDeletedBy(callerId);
    DirectMessage saved = directMessageRepository.save(m);
    DirectConversation conv = loadConversation(saved.getConversationId());
    UUID other = conversationService.otherParticipant(conv, callerId);
    publishToBoth(callerId, other, DirectMessageEventEnvelope.deleted(toDto(saved)));
  }

  public List<DirectMessage> getHistory(UUID conversationId, UUID beforeMessageId, int limit) {
    return beforeMessageId == null
        ? directMessageRepository.findByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequest.of(0, limit))
        : directMessageRepository.findByConversationIdBeforeCursor(
            conversationId, beforeMessageId, PageRequest.of(0, limit));
  }

  public Optional<DirectMessage> lastMessage(UUID conversationId) {
    return directMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId);
  }

  public List<DirectConversation> listConversations(UUID userId) {
    return directConversationRepository.findAllForUser(userId);
  }

  public DirectMessageDTO toDto(DirectMessage m) {
    String displayedText = m.getDeletedAt() == null ? m.getText() : null;
    String senderUsername = resolveUsername(m.getSenderId());
    MessagePreview preview = null;
    if (m.getReplyToId() != null) {
      Optional<DirectMessage> parent = directMessageRepository.findById(m.getReplyToId());
      if (parent.isPresent()) {
        DirectMessage p = parent.get();
        String snippet;
        if (p.getDeletedAt() != null) {
          snippet = MessagePreview.DELETED_PLACEHOLDER;
        } else {
          String t = p.getText() == null ? "" : p.getText();
          snippet = t.length() > MessagePreview.PREVIEW_CHARS
              ? t.substring(0, MessagePreview.PREVIEW_CHARS)
              : t;
        }
        preview = new MessagePreview(p.getId(), resolveUsername(p.getSenderId()), snippet);
      }
    }
    return DirectMessageDTO.builder()
        .id(m.getId())
        .conversationId(m.getConversationId())
        .senderId(m.getSenderId())
        .senderUsername(senderUsername)
        .text(displayedText)
        .createdAt(m.getCreatedAt())
        .editedAt(m.getEditedAt())
        .deletedAt(m.getDeletedAt())
        .deletedBy(m.getDeletedBy())
        .replyTo(preview)
        .build();
  }

  private DirectConversation loadConversation(UUID id) {
    return directConversationRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
  }

  private void validateText(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Message text cannot be empty");
    }
    if (text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("Message exceeds 3072 characters");
    }
  }

  private void ensureFriendsAndNotBanned(UUID me, UUID other) {
    friendshipRepository
        .findBetween(me, other)
        .filter(f -> Friendship.STATUS_ACCEPTED.equals(f.getStatus()))
        .orElseThrow(
            () -> new IllegalArgumentException("You must be friends to send a direct message"));
    if (userBanRepository.existsByBannerIdAndBannedId(me, other)
        || userBanRepository.existsByBannerIdAndBannedId(other, me)) {
      throw new IllegalArgumentException("Cannot send direct message");
    }
  }

  private void publishToBoth(UUID a, UUID b, DirectMessageEventEnvelope env) {
    messagingTemplate.convertAndSendToUser(a.toString(), "/queue/dms", env);
    messagingTemplate.convertAndSendToUser(b.toString(), "/queue/dms", env);
  }

  private String resolveUsername(UUID userId) {
    try {
      User u = userService.getUserById(userId);
      return u.getUsername();
    } catch (IllegalArgumentException e) {
      return userId.toString().substring(0, 8);
    }
  }
}
```

- [ ] **Step 2: Extend `DirectMessageServiceTest.java`**

Keep existing tests. Append the analogous set:

```java
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
```

Make sure the class autowires `DirectMessageRepository`, `ConversationService`, and has a `makeFriends(User, User)` helper (if not, reuse the FriendshipService / FriendshipRepository to create a STATUS_ACCEPTED row between the two users — same helper the existing `FriendsAndDmsFlowIntegrationTest` uses).

- [ ] **Step 3: Run tests**

`cd /src/ai_hakaton/backend && ./gradlew test --tests 'DirectMessageServiceTest'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java \
        backend/src/test/java/com/hackathon/features/dms/DirectMessageServiceTest.java
git commit -m "feat(dms): direct message edit/delete/reply service + WS emission" -m "- send(senderId, conversationId, text, replyToId) validates same-conversation reply target" -m "- editMessage / deleteMessage mirror the room implementation (author-only, tombstone, idempotent)" -m "- toDto blanks text on tombstones and resolves 100-char preview" -m "- Publishes tagged envelopes to both participants via /queue/dms" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: DM `DirectMessageController` — PATCH + DELETE + DTO + WS handler

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/dms/DirectMessageController.java`
- Modify: `backend/src/main/java/com/hackathon/shared/websocket/DirectMessageHandler.java`
- Modify: `backend/src/test/java/com/hackathon/features/dms/DirectMessageControllerTest.java`

- [ ] **Step 1: Replace `DirectMessageController.java`**

```java
package com.hackathon.features.dms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.DirectMessageDTO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DirectMessageController {
  private final DirectMessageService directMessageService;
  private final ConversationService conversationService;
  private final UserService userService;

  record SendMessageBody(String text, UUID replyToId) {}

  record EditMessageBody(String text) {}

  record ConversationView(
      UUID id,
      UUID otherUserId,
      String otherUsername,
      String lastMessage,
      OffsetDateTime lastMessageAt) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping("/conversations")
  public ResponseEntity<List<ConversationView>> listConversations(Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<ConversationView> views =
        directMessageService.listConversations(me).stream()
            .map(
                conv -> {
                  UUID otherId = conversationService.otherParticipant(conv, me);
                  User other = userService.getUserById(otherId);
                  var last = directMessageService.lastMessage(conv.getId()).orElse(null);
                  String lastText = null;
                  OffsetDateTime lastAt = null;
                  if (last != null) {
                    lastText = last.getDeletedAt() == null ? last.getText() : null;
                    lastAt = last.getCreatedAt();
                  }
                  return new ConversationView(
                      conv.getId(), otherId, other.getUsername(), lastText, lastAt);
                })
            .toList();
    return ResponseEntity.ok(views);
  }

  @GetMapping("/with/{otherUserId}")
  public ResponseEntity<DirectConversation> getOrCreate(
      @PathVariable UUID otherUserId, Authentication authentication) {
    return ResponseEntity.ok(
        conversationService.getOrCreate(currentUserId(authentication), otherUserId));
  }

  @GetMapping("/{conversationId}/messages")
  public ResponseEntity<List<DirectMessageDTO>> getHistory(
      @PathVariable UUID conversationId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    List<DirectMessage> messages = directMessageService.getHistory(conversationId, before, limit);
    List<DirectMessageDTO> views = messages.stream().map(directMessageService::toDto).toList();
    return ResponseEntity.ok(views);
  }

  @PostMapping("/{conversationId}/messages")
  public ResponseEntity<DirectMessageDTO> sendMessage(
      @PathVariable UUID conversationId,
      @RequestBody SendMessageBody body,
      Authentication authentication) {
    DirectMessage sent =
        directMessageService.send(
            currentUserId(authentication), conversationId, body.text(), body.replyToId());
    return ResponseEntity.ok(directMessageService.toDto(sent));
  }

  @PatchMapping("/{conversationId}/messages/{messageId}")
  public ResponseEntity<DirectMessageDTO> editMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID messageId,
      @RequestBody EditMessageBody body,
      Authentication authentication) {
    DirectMessage edited =
        directMessageService.editMessage(messageId, currentUserId(authentication), body.text());
    return ResponseEntity.ok(directMessageService.toDto(edited));
  }

  @DeleteMapping("/{conversationId}/messages/{messageId}")
  public ResponseEntity<Void> deleteMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID messageId,
      Authentication authentication) {
    directMessageService.deleteMessage(messageId, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }
}
```

Note: the history endpoint used to return `DirectMessage` entities directly. Frontend adapter in `DirectChatPage` currently reshapes them — once this returns `DirectMessageDTO`, the frontend shape already matches what the component expects (`id`, `conversationId`, `senderId`, `text`, `createdAt` + new fields). `senderUsername` will now be included directly by the backend; Task 9 cleans up the frontend adapter.

- [ ] **Step 2: Replace `DirectMessageHandler.java`**

```java
package com.hackathon.shared.websocket;

import com.hackathon.features.dms.DirectMessageService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageHandler {
  private final DirectMessageService directMessageService;

  public record DmPayload(String text, UUID replyToId) {}

  @MessageMapping("/dms/{conversationId}/message")
  public void handleDirectMessage(
      @DestinationVariable UUID conversationId, DmPayload payload, Principal principal) {
    UUID senderUserId = UUID.fromString(principal.getName());
    // Service publishes the CREATED envelope on /user/{uuid}/queue/dms for both participants.
    directMessageService.send(senderUserId, conversationId, payload.text(), payload.replyToId());
  }
}
```

- [ ] **Step 3: Extend `DirectMessageControllerTest.java`**

Append:

```java
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
```

Add the imports:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

- [ ] **Step 4: Run tests**

`cd /src/ai_hakaton/backend && ./gradlew test --tests 'DirectMessageControllerTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/dms/DirectMessageController.java \
        backend/src/main/java/com/hackathon/shared/websocket/DirectMessageHandler.java \
        backend/src/test/java/com/hackathon/features/dms/DirectMessageControllerTest.java
git commit -m "feat(dms): REST PATCH + DELETE + replyToId; handler uses envelope" -m "- Controller now returns DirectMessageDTO everywhere (history + send + edit)" -m "- PATCH + DELETE endpoints mirror room-message shape" -m "- Handler drops custom DmEvent — service emits DirectMessageEventEnvelope via SimpMessagingTemplate" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Backend integration test — full lifecycle for rooms + DMs

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/integration/MessageContentFlowIntegrationTest.java`

- [ ] **Step 1: Create the file**

```java
package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.dto.DirectMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MessageContentFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired MessageService messageService;
  @Autowired DirectMessageService directMessageService;
  @Autowired ConversationService conversationService;
  @Autowired FriendshipService friendshipService;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void room_send_reply_edit_delete_flow() {
    User author = register("author");
    User peer = register("peer");
    ChatRoom room = chatRoomService.createRoom(
        "flow-" + System.nanoTime(), null, author.getId(), "public");
    chatRoomService.joinRoom(room.getId(), peer.getId());

    Message parent = messageService.sendMessage(room.getId(), author.getId(), "original");
    Message reply = messageService.sendMessage(room.getId(), peer.getId(), "re: original", parent.getId());
    ChatMessageDTO replyDto = messageService.toDto(reply);
    assertNotNull(replyDto.getReplyTo());
    assertEquals("original", replyDto.getReplyTo().textPreview());

    Message edited = messageService.editMessage(reply.getId(), peer.getId(), "re: (edited)");
    assertEquals("re: (edited)", edited.getText());
    assertNotNull(edited.getEditedAt());

    messageService.deleteMessage(parent.getId(), author.getId());
    ChatMessageDTO parentDtoAfter = messageService.toDto(
        messageService.getMessageHistory(room.getId(), null, 50).stream()
            .filter(m -> m.getId().equals(parent.getId()))
            .findFirst().orElseThrow());
    assertNull(parentDtoAfter.getText());
    assertNotNull(parentDtoAfter.getDeletedAt());

    ChatMessageDTO replyDtoAfterParentDelete = messageService.toDto(
        messageService.getMessageHistory(room.getId(), null, 50).stream()
            .filter(m -> m.getId().equals(reply.getId()))
            .findFirst().orElseThrow());
    assertEquals("[deleted]", replyDtoAfterParentDelete.getReplyTo().textPreview());
  }

  @Test
  void dm_send_reply_edit_delete_flow() {
    User a = register("a");
    User b = register("b");
    friendshipService.sendRequest(a.getId(), b.getUsername());
    friendshipService.acceptRequest(b.getId(),
        friendshipService.listIncoming(b.getId()).get(0).getId());
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());

    DirectMessage parent = directMessageService.send(a.getId(), conv.getId(), "hello");
    DirectMessage reply = directMessageService.send(b.getId(), conv.getId(), "hi back", parent.getId());
    DirectMessageDTO replyDto = directMessageService.toDto(reply);
    assertEquals("hello", replyDto.getReplyTo().textPreview());

    DirectMessage edited = directMessageService.editMessage(reply.getId(), b.getId(), "hi back (edited)");
    assertEquals("hi back (edited)", edited.getText());
    assertNotNull(edited.getEditedAt());

    directMessageService.deleteMessage(parent.getId(), a.getId());
    DirectMessageDTO parentAfter = directMessageService.toDto(
        directMessageService.getHistory(conv.getId(), null, 50).stream()
            .filter(m -> m.getId().equals(parent.getId()))
            .findFirst().orElseThrow());
    assertNull(parentAfter.getText());
    assertNotNull(parentAfter.getDeletedAt());
  }
}
```

Note: `friendshipService.sendRequest(UUID senderId, String inviteeUsername)` and `listIncoming(UUID userId)` / `acceptRequest(UUID callerId, UUID requestId)` — if the real service method names differ (check `FriendshipService.java`), adapt to the real names. The existing `FriendsAndDmsFlowIntegrationTest` demonstrates the happy path — mirror its friendship bootstrapping verbatim if signatures diverge.

- [ ] **Step 2: Run it**

`cd /src/ai_hakaton/backend && ./gradlew test --tests 'MessageContentFlowIntegrationTest'`

Expected: 2 tests pass.

- [ ] **Step 3: Run full test suite**

`cd /src/ai_hakaton/backend && ./gradlew test`

Expected: all green.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/test/java/com/hackathon/features/integration/MessageContentFlowIntegrationTest.java
git commit -m "test: end-to-end message content lifecycle — rooms + DMs" -m "Two scenarios: send → reply → edit → delete for both room messages and direct messages. Asserts tombstone, [deleted] preview, editedAt timestamps through service toDto." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Frontend — types + services

**Files:**
- Modify: `frontend/src/types/room.ts`
- Modify: `frontend/src/types/directMessage.ts`
- Modify: `frontend/src/services/messageService.ts`
- Modify: `frontend/src/services/directMessageService.ts`

- [ ] **Step 1: Read current types**

`cat frontend/src/types/room.ts frontend/src/types/directMessage.ts`

- [ ] **Step 2: Extend `frontend/src/types/room.ts`** — add `editedAt`, `deletedAt`, `deletedBy`, `replyTo` fields + the `MessagePreview` type

Replace the `Message` interface (keep other exports intact):

```typescript
export interface MessagePreview {
  id: string;
  authorUsername: string;
  textPreview: string;
}

export interface Message {
  id: string;
  roomId: string;
  userId: string;
  username: string;
  text: string | null;
  createdAt: string;
  editedAt?: string | null;
  deletedAt?: string | null;
  deletedBy?: string | null;
  replyTo?: MessagePreview | null;
}
```

- [ ] **Step 3: Extend `frontend/src/types/directMessage.ts`** — same shape

Add to the file (alongside existing `DirectMessage` / `ConversationView`):

```typescript
import type { MessagePreview } from './room';

export interface DirectMessage {
  id: string;
  conversationId: string;
  senderId: string;
  senderUsername?: string;  // populated by DTO in Feature #5
  text: string | null;
  createdAt: string;
  editedAt?: string | null;
  deletedAt?: string | null;
  deletedBy?: string | null;
  replyTo?: MessagePreview | null;
}
```

Replace the existing `DirectMessage` interface fully — keep `ConversationView` and any other existing exports.

- [ ] **Step 4: Extend `frontend/src/services/messageService.ts`**

Read the file first — the existing shape is small. Add `sendMessage` variant (REST path), `editMessage`, `deleteMessage`:

```typescript
import axios from 'axios';
import type { Message } from '../types/room';

export const messageService = {
  async getHistory(roomId: string, before?: string, limit = 50): Promise<Message[]> {
    const params = new URLSearchParams();
    if (before) params.append('before', before);
    params.append('limit', String(limit));
    return (await axios.get(`/api/rooms/${roomId}/messages?${params}`)).data;
  },

  async sendMessage(
    roomId: string,
    text: string,
    replyToId?: string,
  ): Promise<Message> {
    const body: Record<string, unknown> = { text };
    if (replyToId) body.replyToId = replyToId;
    return (await axios.post(`/api/rooms/${roomId}/messages`, body)).data;
  },

  async editMessage(roomId: string, messageId: string, text: string): Promise<Message> {
    return (await axios.patch(`/api/rooms/${roomId}/messages/${messageId}`, { text })).data;
  },

  async deleteMessage(roomId: string, messageId: string): Promise<void> {
    await axios.delete(`/api/rooms/${roomId}/messages/${messageId}`);
  },
};
```

If the existing file already has `getHistory` with a different path or signature, preserve that path but refresh the response type to `Message[]`.

- [ ] **Step 5: Extend `frontend/src/services/directMessageService.ts`**

Replace entire file:

```typescript
import axios from 'axios';
import type { ConversationView, DirectConversation, DirectMessage } from '../types/directMessage';

export const directMessageService = {
  async listConversations(): Promise<ConversationView[]> {
    return (await axios.get('/api/dms/conversations')).data;
  },
  async getOrCreateWith(otherUserId: string): Promise<DirectConversation> {
    return (await axios.get(`/api/dms/with/${otherUserId}`)).data;
  },
  async getHistory(
    conversationId: string,
    before?: string,
    limit = 50,
  ): Promise<DirectMessage[]> {
    const params = new URLSearchParams();
    if (before) params.append('before', before);
    params.append('limit', String(limit));
    return (await axios.get(`/api/dms/${conversationId}/messages?${params}`)).data;
  },
  async sendMessage(
    conversationId: string,
    text: string,
    replyToId?: string,
  ): Promise<DirectMessage> {
    const body: Record<string, unknown> = { text };
    if (replyToId) body.replyToId = replyToId;
    return (await axios.post(`/api/dms/${conversationId}/messages`, body)).data;
  },
  async editMessage(
    conversationId: string,
    messageId: string,
    text: string,
  ): Promise<DirectMessage> {
    return (await axios.patch(`/api/dms/${conversationId}/messages/${messageId}`, { text })).data;
  },
  async deleteMessage(conversationId: string, messageId: string): Promise<void> {
    await axios.delete(`/api/dms/${conversationId}/messages/${messageId}`);
  },
};
```

- [ ] **Step 6: Build**

`cd /src/ai_hakaton/frontend && npm run build`

Expected: clean. If `DirectChatPage.tsx` uses `(m as any).senderUsername` that still works; real cleanup in Task 12.

- [ ] **Step 7: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/types/room.ts frontend/src/types/directMessage.ts \
        frontend/src/services/messageService.ts frontend/src/services/directMessageService.ts
git commit -m "feat(frontend): message content types + service methods" -m "- Message + DirectMessage gain editedAt, deletedAt, deletedBy, replyTo (MessagePreview)" -m "- messageService + directMessageService gain editMessage, deleteMessage; sendMessage accepts optional replyToId" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Frontend — WS handlers branch on event type (rooms + DMs)

**Files:**
- Modify: `frontend/src/hooks/useWebSocket.ts`
- Modify: `frontend/src/hooks/useDirectMessageSocket.ts`
- Modify: `frontend/src/hooks/useRoomMessages.ts`
- Modify: `frontend/src/hooks/useDirectMessages.ts`

- [ ] **Step 1: Read current files**

`cat frontend/src/hooks/useWebSocket.ts frontend/src/hooks/useDirectMessageSocket.ts frontend/src/hooks/useRoomMessages.ts frontend/src/hooks/useDirectMessages.ts`

- [ ] **Step 2: Upgrade `useWebSocket.ts`**

Open `frontend/src/hooks/useWebSocket.ts`. Changes:
1. `subscribe(roomId, handler)` — `handler` now receives a tagged envelope `{ type: 'CREATED' | 'EDITED' | 'DELETED', message: Message }` rather than a raw `Message`. Internally the callback passes the parsed JSON payload straight through.
2. `sendMessage(roomId, text, replyToId?)` — STOMP body now includes `replyToId`.

Replace the relevant methods. The file's existing structure should be preserved; only the two public methods shown below change. If you're unsure about the file's overall shape, treat this as a full replacement preserving the existing connection/reconnection code and the current handler registry — the key delta is the two functions below.

```typescript
// Exported type used by callers
export type RoomMessageEvent =
  | { type: 'CREATED'; message: Message }
  | { type: 'EDITED';  message: Message }
  | { type: 'DELETED'; message: { id: string; deletedAt: string; deletedBy: string } };

// ... rest of hook body
subscribe: (roomId: string, onEvent: (event: RoomMessageEvent) => void) => {
  // existing client.subscribe(`/topic/room/${roomId}`, (frame) => {...}):
  // adjust so onEvent receives the parsed envelope directly.
  const sub = client.subscribe(`/topic/room/${roomId}`, (frame) => {
    const payload = JSON.parse(frame.body) as RoomMessageEvent;
    onEvent(payload);
  });
  // ... store sub per roomId as before
},

sendMessage: (roomId: string, text: string, replyToId?: string) => {
  client.publish({
    destination: `/app/rooms/${roomId}/message`,
    body: JSON.stringify({ text, replyToId: replyToId ?? null }),
  });
},
```

Import `Message` from `../types/room` if not already present.

- [ ] **Step 3: Upgrade `useDirectMessageSocket.ts`**

Same pattern:

```typescript
export type DirectMessageEvent =
  | { type: 'CREATED'; message: DirectMessage }
  | { type: 'EDITED';  message: DirectMessage }
  | { type: 'DELETED'; message: { id: string; deletedAt: string; deletedBy: string } };
```

Change the subscription callback to forward the tagged payload and update `sendDm(conversationId, text, replyToId?)` to include `replyToId` in the STOMP publish body.

Preserve any existing reconnection/logging logic verbatim; just swap the callback shape and add the extra param.

- [ ] **Step 4: Upgrade `useRoomMessages.ts`**

Open the file. Add two helpers and change the WS-event handler to branch on `event.type`:

```typescript
const addMessage = (m: Message) => {
  setMessages((prev) => {
    if (prev.some((p) => p.id === m.id)) return prev;
    return [...prev, m];
  });
};

const upsertMessage = (m: Message) => {
  setMessages((prev) => prev.map((p) => (p.id === m.id ? { ...p, ...m } : p)));
};

const markDeleted = (id: string, deletedAt: string, deletedBy: string) => {
  setMessages((prev) =>
    prev.map((p) => (p.id === id ? { ...p, text: null, deletedAt, deletedBy } : p)),
  );
};

// The dispatcher the component passes to subscribe():
const handleEvent = (event: RoomMessageEvent) => {
  if (event.type === 'CREATED') addMessage(event.message);
  else if (event.type === 'EDITED') upsertMessage(event.message);
  else if (event.type === 'DELETED')
    markDeleted(event.message.id, event.message.deletedAt, event.message.deletedBy);
};

// Expose from the hook:
return { messages, loadInitialMessages, loadMoreMessages, addMessage, upsertMessage, markDeleted, handleEvent, /* plus existing returns */ };
```

Import `RoomMessageEvent` from `./useWebSocket`. Keep the existing returns from the hook (don't drop `loadInitialMessages`, `loadMoreMessages`, etc.).

- [ ] **Step 5: Upgrade `useDirectMessages.ts`**

Same pattern — add `upsertMessage`, `markDeleted`, `handleEvent`, `DirectMessageEvent` import from `./useDirectMessageSocket`.

- [ ] **Step 6: Build**

`cd /src/ai_hakaton/frontend && npm run build`

Expected: clean. If `ChatPage` / `DirectChatPage` still pass `addMessage` to `subscribe` / `onDm`, their compilation may fail because the callback shape changed. That's addressed in the next steps — update both page `subscribe(...)` call sites to pass the new `handleEvent` instead of `addMessage`:

- In `ChatPage.tsx`: `subscribe(roomId, addMessage)` → `subscribe(roomId, handleEvent)`; import `handleEvent` from the hook return.
- In `DirectChatPage.tsx`: `const { sendDm } = useDirectMessageSocket(onDm, () => {});` — change `onDm` to forward the tagged payload to `handleEvent`, or directly pass `handleEvent`:

```tsx
const { sendDm } = useDirectMessageSocket((event) => handleEvent(event), () => {});
```

where `handleEvent` comes from `useDirectMessages`.

Make these page-level changes inside this task's commit since they're a direct consequence of the hook API change. Build + vitest must pass.

- [ ] **Step 7: Run vitest**

`cd /src/ai_hakaton/frontend && npm test -- --run`

Expected: green. If a unit test imports the old hook shape, adjust narrowly.

- [ ] **Step 8: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/hooks/useWebSocket.ts frontend/src/hooks/useDirectMessageSocket.ts \
        frontend/src/hooks/useRoomMessages.ts frontend/src/hooks/useDirectMessages.ts \
        frontend/src/pages/ChatPage.tsx frontend/src/pages/DirectChatPage.tsx
git commit -m "feat(frontend): WS event tagged union + upsert/tombstone helpers" -m "- useWebSocket / useDirectMessageSocket publish + subscribe using the new {type, message} envelope" -m "- sendMessage / sendDm accept optional replyToId" -m "- useRoomMessages / useDirectMessages expose handleEvent dispatching CREATED/EDITED/DELETED" -m "- ChatPage and DirectChatPage pass handleEvent to the sockets" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Frontend — `MessageItem` with hover actions, tombstone, reply header, edit mode

**Files:**
- Create: `frontend/src/components/MessageItem.tsx`
- Create: `frontend/src/components/MessageActionsMenu.tsx`
- Create: `frontend/src/components/InlineMessageEditor.tsx`
- Modify: `frontend/src/components/MessageList.tsx`

- [ ] **Step 1: Create `frontend/src/components/MessageActionsMenu.tsx`**

```tsx
import React from 'react';

interface Props {
  isAuthor: boolean;
  onReply: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

export const MessageActionsMenu: React.FC<Props> = ({ isAuthor, onReply, onEdit, onDelete }) => (
  <div className="absolute top-1 right-2 hidden group-hover:flex gap-1 bg-white border rounded shadow px-1 py-0.5 text-xs">
    <button onClick={onReply} className="px-2 py-0.5 hover:bg-gray-100 rounded" aria-label="Reply">
      ↩ Reply
    </button>
    {isAuthor && (
      <>
        <button onClick={onEdit} className="px-2 py-0.5 hover:bg-gray-100 rounded" aria-label="Edit">
          ✎ Edit
        </button>
        <button
          onClick={onDelete}
          className="px-2 py-0.5 hover:bg-red-50 text-red-600 rounded"
          aria-label="Delete"
        >
          🗑 Delete
        </button>
      </>
    )}
  </div>
);
```

- [ ] **Step 2: Create `frontend/src/components/InlineMessageEditor.tsx`**

```tsx
import React, { useState } from 'react';

interface Props {
  initialText: string;
  onSave: (text: string) => Promise<void>;
  onCancel: () => void;
}

const MAX = 3072;

export const InlineMessageEditor: React.FC<Props> = ({ initialText, onSave, onCancel }) => {
  const [text, setText] = useState(initialText);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    const trimmed = text.trim();
    if (!trimmed) return;
    if (trimmed === initialText) {
      onCancel();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSave(trimmed);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      save();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onCancel();
    }
  };

  return (
    <div className="space-y-1">
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value.slice(0, MAX))}
        onKeyDown={onKeyDown}
        rows={3}
        disabled={busy}
        className="w-full border rounded px-2 py-1 resize-none text-sm"
        autoFocus
      />
      {error && <div className="text-xs text-red-500">{error}</div>}
      <div className="flex gap-2 text-xs">
        <button
          onClick={save}
          disabled={busy || !text.trim()}
          className="px-3 py-1 bg-blue-500 text-white rounded disabled:bg-gray-400"
        >
          {busy ? 'Saving…' : 'Save'}
        </button>
        <button onClick={onCancel} disabled={busy} className="px-3 py-1 border rounded">
          Cancel
        </button>
        <span className="text-gray-400 self-center">Ctrl+Enter · Esc</span>
      </div>
    </div>
  );
};
```

- [ ] **Step 3: Create `frontend/src/components/MessageItem.tsx`**

```tsx
import React, { useState } from 'react';
import type { Message } from '../types/room';
import { MessageActionsMenu } from './MessageActionsMenu';
import { InlineMessageEditor } from './InlineMessageEditor';

interface Props {
  message: Message;
  currentUserId: string | null;
  onReply: (m: Message) => void;
  onEdit: (messageId: string, newText: string) => Promise<void>;
  onDelete: (messageId: string) => Promise<void>;
}

export const MessageItem: React.FC<Props> = ({ message, currentUserId, onReply, onEdit, onDelete }) => {
  const [editing, setEditing] = useState(false);
  const isDeleted = !!message.deletedAt;
  const isAuthor = !!currentUserId && message.userId === currentUserId;
  const edited = !!message.editedAt;

  const timestamp = new Date(message.createdAt).toLocaleTimeString();

  if (isDeleted) {
    return (
      <div className="bg-gray-50 rounded p-3 border-l-4 border-gray-300">
        <div className="flex justify-between items-baseline">
          <span className="font-semibold text-sm text-gray-400">{message.username}</span>
          <span className="text-xs text-gray-400">{timestamp}</span>
        </div>
        <p className="text-gray-400 italic mt-1">Message deleted</p>
      </div>
    );
  }

  return (
    <div className="group relative bg-gray-50 rounded p-3 border-l-4 border-blue-500">
      <MessageActionsMenu
        isAuthor={isAuthor}
        onReply={() => onReply(message)}
        onEdit={() => setEditing(true)}
        onDelete={async () => {
          if (window.confirm('Delete this message?')) {
            await onDelete(message.id);
          }
        }}
      />

      <div className="flex justify-between items-baseline">
        <span className="font-semibold text-sm">{message.username}</span>
        <span className="text-xs text-gray-400">
          {timestamp}
          {edited && <span className="ml-1">(edited)</span>}
        </span>
      </div>

      {message.replyTo && (
        <a
          href={`#msg-${message.replyTo.id}`}
          className="block border-l-2 border-gray-300 pl-2 mt-1 mb-1 text-xs text-gray-500 truncate"
        >
          <strong>@{message.replyTo.authorUsername}</strong>: {message.replyTo.textPreview}
        </a>
      )}

      <div id={`msg-${message.id}`}>
        {editing ? (
          <InlineMessageEditor
            initialText={message.text ?? ''}
            onSave={async (newText) => {
              await onEdit(message.id, newText);
              setEditing(false);
            }}
            onCancel={() => setEditing(false)}
          />
        ) : (
          <p className="text-gray-700 mt-1 whitespace-pre-wrap">{message.text}</p>
        )}
      </div>
    </div>
  );
};
```

- [ ] **Step 4: Modify `frontend/src/components/MessageList.tsx`**

Replace contents with:

```tsx
import React, { useEffect, useRef } from 'react';
import type { Message } from '../types/room';
import { MessageItem } from './MessageItem';

interface MessageListProps {
  messages: Message[];
  isLoading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  currentUserId: string | null;
  onReply: (m: Message) => void;
  onEdit: (messageId: string, newText: string) => Promise<void>;
  onDelete: (messageId: string) => Promise<void>;
}

export const MessageList: React.FC<MessageListProps> = ({
  messages,
  isLoading,
  hasMore,
  onLoadMore,
  currentUserId,
  onReply,
  onEdit,
  onDelete,
}) => {
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = 0;
    }
  }, [messages]);

  return (
    <div
      ref={listRef}
      className="flex-1 min-h-0 overflow-y-auto bg-white p-4 border rounded mb-4"
    >
      {hasMore && !isLoading && (
        <button
          onClick={onLoadMore}
          className="w-full text-center text-blue-500 text-sm mb-4 hover:underline"
        >
          Load older messages
        </button>
      )}
      {isLoading && <div className="text-center text-gray-500">Loading...</div>}
      {messages.length === 0 && !isLoading && (
        <div className="text-center text-gray-500">No messages yet</div>
      )}
      <div className="space-y-3">
        {messages.map((m) => (
          <MessageItem
            key={m.id}
            message={m}
            currentUserId={currentUserId}
            onReply={onReply}
            onEdit={onEdit}
            onDelete={onDelete}
          />
        ))}
      </div>
    </div>
  );
};
```

- [ ] **Step 5: Build**

`cd /src/ai_hakaton/frontend && npm run build`

Expected to fail because `ChatPage` / `DirectChatPage` no longer satisfy `MessageList`'s new required props. This is intentional — Task 11 (page wiring) fixes both in the next commit. **Stop here WITHOUT committing.** The next task finishes the commit that makes the build green.

- [ ] **Step 6 (no commit yet — proceed to Task 11)**

Skip commit; move to Task 11. Tasks 10 and 11 land as a single commit at the end of Task 11.

---

### Task 11: Frontend — `ReplyPill`, page wiring (ChatPage + DirectChatPage)

**Files:**
- Create: `frontend/src/components/ReplyPill.tsx`
- Modify: `frontend/src/pages/ChatPage.tsx`
- Modify: `frontend/src/pages/DirectChatPage.tsx`

- [ ] **Step 1: Create `frontend/src/components/ReplyPill.tsx`**

```tsx
import React from 'react';

interface Props {
  authorUsername: string;
  textPreview: string;
  onDismiss: () => void;
}

export const ReplyPill: React.FC<Props> = ({ authorUsername, textPreview, onDismiss }) => (
  <div className="flex items-center gap-2 bg-blue-50 border border-blue-200 rounded px-3 py-1 text-xs">
    <span className="truncate">
      Replying to <strong>@{authorUsername}</strong>: {textPreview}
    </span>
    <button
      onClick={onDismiss}
      className="ml-auto text-gray-500 hover:text-gray-700"
      aria-label="Cancel reply"
    >
      ×
    </button>
  </div>
);
```

- [ ] **Step 2: Replace `frontend/src/pages/ChatPage.tsx`**

```tsx
import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import { useWebSocket } from '../hooks/useWebSocket';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { ReplyPill } from '../components/ReplyPill';
import { roomService } from '../services/roomService';
import { messageService } from '../services/messageService';
import type { Message } from '../types/room';

const getCurrentUserId = (): string | null => {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
};

export const ChatPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const currentUserId = useMemo(() => getCurrentUserId(), []);
  const { currentRoom, fetchRoom, leaveRoom } = useRoom();
  const { messages, loadInitialMessages, loadMoreMessages, handleEvent } =
    useRoomMessages(roomId);
  const {
    isConnected,
    subscribe,
    unsubscribe,
    sendMessage: sendWebSocketMessage,
  } = useWebSocket();

  const [replyTarget, setReplyTarget] = useState<Message | null>(null);

  useEffect(() => {
    if (!roomId) return;
    fetchRoom(roomId);
    loadInitialMessages(roomId);
    roomService.joinRoom(roomId).catch(() => {});

    if (isConnected) {
      subscribe(roomId, handleEvent);
    }

    return () => {
      if (roomId) unsubscribe(roomId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, isConnected]);

  const handleSend = (text: string) => {
    if (roomId && isConnected) {
      try {
        sendWebSocketMessage(roomId, text, replyTarget?.id);
        setReplyTarget(null);
      } catch (err) {
        console.error('Failed to send message:', err);
      }
    }
  };

  const handleEdit = async (messageId: string, newText: string) => {
    if (!roomId) return;
    await messageService.editMessage(roomId, messageId, newText);
    // Server emits WS EDITED; local upsert happens via handleEvent.
  };

  const handleDelete = async (messageId: string) => {
    if (!roomId) return;
    await messageService.deleteMessage(roomId, messageId);
  };

  const handleLeaveRoom = async () => {
    if (roomId) {
      await leaveRoom(roomId);
      navigate('/rooms');
    }
  };

  const replyPreview = replyTarget && {
    authorUsername: replyTarget.username,
    textPreview: (replyTarget.text ?? '').slice(0, 100),
  };

  return (
    <div className="h-full bg-gray-100 flex flex-col min-h-0">
      <div className="bg-white shadow p-4 border-b">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold">{currentRoom?.name || 'Loading...'}</h1>
            {currentRoom?.description && (
              <p className="text-gray-600 text-sm">{currentRoom.description}</p>
            )}
          </div>
          <button
            onClick={handleLeaveRoom}
            className="px-4 py-2 border rounded hover:bg-gray-100"
          >
            Leave Room
          </button>
        </div>
      </div>

      <div className="flex flex-1 min-h-0 w-full">
        <div className="flex-1 flex flex-col min-w-0 min-h-0">
          <MessageList
            messages={messages}
            isLoading={false}
            hasMore={true}
            onLoadMore={loadMoreMessages}
            currentUserId={currentUserId}
            onReply={(m) => setReplyTarget(m)}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
          <MessageInput
            onSend={handleSend}
            disabled={!isConnected}
            actions={
              replyPreview ? (
                <ReplyPill
                  authorUsername={replyPreview.authorUsername}
                  textPreview={replyPreview.textPreview}
                  onDismiss={() => setReplyTarget(null)}
                />
              ) : null
            }
          />
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 3: Replace `frontend/src/pages/DirectChatPage.tsx`**

```tsx
import React, { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { ReplyPill } from '../components/ReplyPill';
import { useDirectMessages } from '../hooks/useDirectMessages';
import { useDirectMessageSocket } from '../hooks/useDirectMessageSocket';
import { directMessageService } from '../services/directMessageService';
import type { DirectMessage } from '../types/directMessage';
import type { Message } from '../types/room';

const getCurrentUserId = (): string | null => {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
};

export const DirectChatPage: React.FC = () => {
  const { conversationId } = useParams<{ conversationId: string }>();
  const currentUserId = useMemo(() => getCurrentUserId(), []);
  const { messages, hasMore, isLoading, loadInitial, loadMore, handleEvent } =
    useDirectMessages(conversationId);

  const [replyTarget, setReplyTarget] = useState<DirectMessage | null>(null);

  const { sendDm } = useDirectMessageSocket(
    (event) => {
      if (event.type === 'CREATED' || event.type === 'EDITED') {
        if (event.message.conversationId !== conversationId) return;
      }
      handleEvent(event);
    },
    () => {},
  );

  useEffect(() => {
    if (conversationId) loadInitial(conversationId);
  }, [conversationId, loadInitial]);

  const handleSend = (text: string) => {
    if (conversationId) {
      sendDm(conversationId, text, replyTarget?.id);
      setReplyTarget(null);
    }
  };

  const handleEdit = async (messageId: string, newText: string) => {
    if (!conversationId) return;
    await directMessageService.editMessage(conversationId, messageId, newText);
  };

  const handleDelete = async (messageId: string) => {
    if (!conversationId) return;
    await directMessageService.deleteMessage(conversationId, messageId);
  };

  // MessageItem / MessageList consume the room-style Message shape. Adapt.
  const adapted: Message[] = messages.map((m) => ({
    id: m.id,
    roomId: m.conversationId,
    userId: m.senderId,
    username: m.senderUsername ?? (m.senderId ? m.senderId.slice(0, 8) : 'unknown'),
    text: m.text,
    createdAt: m.createdAt,
    editedAt: m.editedAt,
    deletedAt: m.deletedAt,
    deletedBy: m.deletedBy,
    replyTo: m.replyTo,
  }));

  const replyPreview = replyTarget && {
    authorUsername: replyTarget.senderUsername ?? 'unknown',
    textPreview: (replyTarget.text ?? '').slice(0, 100),
  };

  const onReplyAdapter = (msg: Message) => {
    const original = messages.find((m) => m.id === msg.id);
    if (original) setReplyTarget(original);
  };

  return (
    <div className="flex flex-col h-full min-h-0">
      <div className="bg-white shadow p-4 border-b">
        <h1 className="text-xl font-bold">Direct Message</h1>
      </div>
      <MessageList
        messages={adapted}
        isLoading={isLoading}
        hasMore={hasMore}
        onLoadMore={loadMore}
        currentUserId={currentUserId}
        onReply={onReplyAdapter}
        onEdit={handleEdit}
        onDelete={handleDelete}
      />
      <MessageInput
        onSend={handleSend}
        disabled={!conversationId}
        actions={
          replyPreview ? (
            <ReplyPill
              authorUsername={replyPreview.authorUsername}
              textPreview={replyPreview.textPreview}
              onDismiss={() => setReplyTarget(null)}
            />
          ) : null
        }
      />
    </div>
  );
};
```

- [ ] **Step 4: Build + vitest**

`cd /src/ai_hakaton/frontend && npm run build && npm test -- --run`

Expected: clean + vitest green. Fix narrow test breakages only (e.g., if any existing vitest test mounted `MessageList` with the old props, update those mount calls to include the four new required props or wrap in a helper).

- [ ] **Step 5: Commit Tasks 10 + 11 together**

```bash
cd /src/ai_hakaton
git add frontend/src/components/MessageItem.tsx \
        frontend/src/components/MessageActionsMenu.tsx \
        frontend/src/components/InlineMessageEditor.tsx \
        frontend/src/components/MessageList.tsx \
        frontend/src/components/ReplyPill.tsx \
        frontend/src/pages/ChatPage.tsx \
        frontend/src/pages/DirectChatPage.tsx
git commit -m "feat(frontend): reply pill + inline edit + tombstone UX for rooms and DMs" -m "- Extract MessageItem from MessageList with hover actions (Reply / Edit / Delete)" -m "- Deleted message renders italic 'Message deleted' tombstone" -m "- Edited message shows '(edited)' after timestamp" -m "- Reply-to header renders a border-left quote bar; anchor links to the parent" -m "- Composer renders a ReplyPill (via MessageInput.actions slot) while replying" -m "- Inline textarea editor saves via PATCH (Ctrl+Enter) / cancels (Esc)" -m "- ChatPage and DirectChatPage thread the callbacks through and deduplicate DM conversation events" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Playwright E2E — `message-content.spec.ts`

**Files:**
- Create: `frontend/e2e/message-content.spec.ts`

- [ ] **Step 1: Rebuild backend container to pick up the new endpoints**

```bash
cd /src/ai_hakaton && docker compose up -d --build backend
docker logs chat-backend --tail 5
```

Wait until "Started ChatServerApplication" appears in the logs.

- [ ] **Step 2: Create `frontend/e2e/message-content.spec.ts`**

```typescript
import { test, expect, Browser, Page } from '@playwright/test';

const password = 'password123';

function uniqueUser(prefix: string) {
  const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
  const username = `${prefix}${stamp}`;
  return { username, email: `${username}@example.com`, password };
}

async function registerAndLogin(
  browser: Browser,
  email: string,
  username: string,
  pw: string,
): Promise<{ ctx: Awaited<ReturnType<Browser['newContext']>>; page: Page }> {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto('/register');
  await page.fill('#email', email);
  await page.fill('#username', username);
  await page.fill('#password', pw);
  await page.fill('#confirm-password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/login$/);
  await page.fill('#email', email);
  await page.fill('#password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/rooms$/);
  return { ctx, page };
}

test.describe('Message content — rooms', () => {
  test('reply + edit + delete flow visible to both clients', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const bob = uniqueUser('bob');

    const { ctx: aliceCtx, page: alicePage } = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );
    const roomName = `msg-${Date.now().toString().slice(-7)}`;
    await alicePage.getByRole('button', { name: '+ Create room' }).click();
    await alicePage.fill('input[placeholder="Enter room name"]', roomName);
    await alicePage.click('div.fixed button:has-text("Create"):not(:has-text("Cancel"))');
    await alicePage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    const roomUrl = alicePage.url();
    const textarea = alicePage.getByPlaceholder(/type a message/i);
    await textarea.fill('hello from alice');
    await alicePage.keyboard.press('Control+Enter');
    await expect(alicePage.locator('body')).toContainText('hello from alice');

    // Bob joins the same room
    const { ctx: bobCtx, page: bobPage } = await registerAndLogin(
      browser,
      bob.email,
      bob.username,
      bob.password,
    );
    await bobPage.goto(roomUrl);
    await bobPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await expect(bobPage.locator('body')).toContainText('hello from alice');

    // Bob replies
    const aliceRow = bobPage.locator('div').filter({ hasText: /^\s*alice.*hello from alice/ }).first();
    await aliceRow.hover();
    await bobPage.getByRole('button', { name: /Reply/ }).first().click();
    await expect(bobPage.locator('body')).toContainText(/Replying to/);
    const bobInput = bobPage.getByPlaceholder(/type a message/i);
    await bobInput.fill('hi alice');
    await bobPage.keyboard.press('Control+Enter');
    await expect(bobPage.locator('body')).toContainText('hi alice');
    await expect(alicePage.locator('body')).toContainText('hi alice', { timeout: 5_000 });

    // Alice edits her message
    const aliceOwnRow = alicePage.locator('div').filter({ hasText: 'hello from alice' }).first();
    await aliceOwnRow.hover();
    await alicePage.getByRole('button', { name: /Edit/ }).first().click();
    const editor = alicePage.locator('textarea').nth(1); // inline editor
    await editor.fill('hello from alice (edited content)');
    await alicePage.keyboard.press('Control+Enter');
    await expect(alicePage.locator('body')).toContainText('(edited)', { timeout: 5_000 });
    await expect(bobPage.locator('body')).toContainText('hello from alice (edited content)', { timeout: 5_000 });

    // Alice deletes her message
    alicePage.once('dialog', (d) => d.accept());
    const aliceEditedRow = alicePage.locator('div').filter({ hasText: /edited content/ }).first();
    await aliceEditedRow.hover();
    await alicePage.getByRole('button', { name: /Delete/ }).first().click();
    await expect(alicePage.locator('body')).toContainText(/Message deleted/);
    await expect(bobPage.locator('body')).toContainText(/Message deleted/, { timeout: 5_000 });

    await aliceCtx.close();
    await bobCtx.close();
  });
});
```

- [ ] **Step 3: Run the suite**

```bash
cd /src/ai_hakaton/frontend && npm run test:e2e -- --reporter=line
```

Expected: full suite green (existing + the new scenario). If selectors in the new spec are brittle, prefer scoping via role + name over complex `has-text` chains — but keep changes narrow.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add frontend/e2e/message-content.spec.ts
git commit -m "test(e2e): two-browser message content lifecycle (reply + edit + delete)" -m "Alice + Bob register, Alice sends, Bob replies (with reply pill + header), Alice edits (both see '(edited)'), Alice deletes (both see tombstone). Uses Ctrl+Enter to send." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: Roadmap update

**Files:**
- Modify: `FEATURES_ROADMAP.md`

- [ ] **Step 1: Move Feature #5 (content) from Planned to Completed**

Find the `### Feature #5 (planned content): Message Content Enhancements` block. Replace it with:

```markdown
### Feature #5 (content): Message Content Enhancements ✅
- Reply / quote-to-message — flat `reply_to_id` reference; quote header with author + 100-char preview; `[deleted]` placeholder when parent is soft-deleted
- Author edit with `(edited)` indicator — author-only; blocked on tombstone; `editedAt` timestamp on the DTO
- Author soft-delete — author-only; idempotent; renders as muted italic `Message deleted`; original text retained in DB for audit
- Applies uniformly to room messages and direct messages (same schema delta, same service methods, mirrored DTOs)
- WebSocket payload promoted to tagged union `{type: CREATED | EDITED | DELETED, message}` on both `/topic/room/{roomId}` and `/user/{uuid}/queue/dms`
- Frontend: `MessageItem` extraction, `MessageActionsMenu` hover bar, `ReplyPill` composer chip, `InlineMessageEditor`, `ComposerActions` slot finally used
- Multi-line + Unicode emoji already worked (native textarea input)
- Spec: `docs/superpowers/specs/2026-04-18-message-content-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-message-content.md` (13 tasks — all complete)
- **Status: COMPLETE**
```

And move it into the `## Completed Features` section (below Execution #5: App Shell Refactor).

- [ ] **Step 2: Update the Progress block**

Replace:

```markdown
## Progress
- **Completed:** 5 execution slots (Features #1, #2, #3, #4, App Shell Refactor)
- **In progress:** 0
- **Remaining:** 4 (Message Content, Attachments, Presence/Sessions, Account Management)
```

with:

```markdown
## Progress
- **Completed:** 6 execution slots (Features #1, #2, #3, #4, App Shell Refactor, Message Content)
- **In progress:** 0
- **Remaining:** 3 (Attachments, Presence/Sessions, Account Management)
```

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add FEATURES_ROADMAP.md
git commit -m "docs(roadmap): Feature #5 content (reply + edit + delete) complete" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Verification Checklist

Before considering Feature #5 content shipped:

- [ ] `./gradlew test` — full backend suite green (existing + new service/controller/integration tests)
- [ ] `npm run build` — clean
- [ ] `npm test -- --run` — vitest green
- [ ] `npm run test:e2e` — Playwright green (including `message-content.spec.ts`)
- [ ] `docker compose up -d --build backend` — container starts cleanly; Flyway applies V5
- [ ] Manual smoke: open a room as two users; send, reply, edit (watch `(edited)` on the peer), delete (watch tombstone on the peer). Repeat on DMs.
- [ ] `FEATURES_ROADMAP.md` reflects the completed feature.
