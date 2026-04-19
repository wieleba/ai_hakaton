# Account Management (Feature #8) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Logged-in users can change their password and permanently delete their account; deletion cascades through owned rooms, memberships, friendships, DMs, reactions, bans, and invitations. Messages in rooms they did not own survive with a null author.

**Architecture:** V7 Flyway migration rewrites every user-referencing FK with the correct `ON DELETE` semantics (CASCADE or SET NULL). New service methods `UserService.changePassword` + `UserService.deleteAccount` do the work. `UserController` exposes `PATCH /api/users/me/password` and `DELETE /api/users/me`. `JwtAuthenticationFilter` gains a DB existence check so stale tokens 401 cleanly. Frontend extends the existing profile dropdown with two modals.

**Tech Stack:** Spring Boot 3.5.12, Java 25, PostgreSQL 15, Flyway, JPA, Spring Security, JUnit 5, MockMvc; React 19, TypeScript, Vite, axios, Playwright.

**Spec:** `docs/superpowers/specs/2026-04-19-account-management-design.md`

---

## File Structure

### Backend

```
backend/src/main/resources/db/migration/
  V7__account_management_cascades.sql                       (new)

backend/src/main/java/com/hackathon/features/users/
  UserService.java                                           (+changePassword, +deleteAccount)
  UserController.java                                        (+PATCH /me/password, +DELETE /me)
  UserRepository.java                                        (no change)
  User.java                                                  (no change)

backend/src/main/java/com/hackathon/features/messages/
  MessageService.java                                        (null-author fallback in resolveUsername)

backend/src/main/java/com/hackathon/features/dms/
  DirectMessageService.java                                  (same fallback)

backend/src/main/java/com/hackathon/shared/security/
  JwtAuthenticationFilter.java                               (+existence check)

backend/src/test/java/com/hackathon/features/users/
  UserServiceTest.java                                       (extend — change + delete)
  UserControllerTest.java                                    (extend — PATCH + DELETE)

backend/src/test/java/com/hackathon/features/integration/
  AccountDeletionFlowIntegrationTest.java                    (new)

backend/src/test/java/com/hackathon/shared/security/
  JwtAuthenticationFilterExistenceTest.java                  (new)
```

### Frontend

```
frontend/src/services/
  accountService.ts                                          (new — PATCH password, DELETE me)

frontend/src/components/
  ChangePasswordModal.tsx                                    (new)
  DeleteAccountModal.tsx                                     (new)

frontend/src/layout/
  ProfileMenu.tsx                                            (+Change password, +Delete account)

frontend/e2e/
  account-management.spec.ts                                 (new)

FEATURES_ROADMAP.md                                          (Feature #8 → COMPLETE)
```

---

## Implementation Tasks

### Task 1: V7 migration — rewrite user-referencing FKs

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__account_management_cascades.sql`

- [ ] **Step 1: Create the migration**

```sql
-- V7: rewrite every user-referencing FK to support account deletion.
-- Dev-only migration — user has confirmed "don't worry about migrating data".

-- Allow messages.user_id to be NULL so author-deleted messages survive.
ALTER TABLE messages ALTER COLUMN user_id DROP NOT NULL;

-- chat_rooms.owner_id → CASCADE (deleting an owner deletes their rooms).
ALTER TABLE chat_rooms DROP CONSTRAINT fk_chat_rooms_owner;
ALTER TABLE chat_rooms
  ADD CONSTRAINT fk_chat_rooms_owner
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

-- room_members.user_id → CASCADE (memberships removed with the user).
ALTER TABLE room_members DROP CONSTRAINT fk_room_members_user;
ALTER TABLE room_members
  ADD CONSTRAINT fk_room_members_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- messages.user_id → SET NULL (messages in non-owned rooms survive as "Deleted user").
ALTER TABLE messages DROP CONSTRAINT fk_messages_user;
ALTER TABLE messages
  ADD CONSTRAINT fk_messages_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- friendships — CASCADE both sides.
ALTER TABLE friendships DROP CONSTRAINT friendships_requester_id_fkey;
ALTER TABLE friendships
  ADD CONSTRAINT friendships_requester_id_fkey
  FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE friendships DROP CONSTRAINT friendships_addressee_id_fkey;
ALTER TABLE friendships
  ADD CONSTRAINT friendships_addressee_id_fkey
  FOREIGN KEY (addressee_id) REFERENCES users(id) ON DELETE CASCADE;

-- user_bans — CASCADE both sides.
ALTER TABLE user_bans DROP CONSTRAINT user_bans_banner_id_fkey;
ALTER TABLE user_bans
  ADD CONSTRAINT user_bans_banner_id_fkey
  FOREIGN KEY (banner_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_bans DROP CONSTRAINT user_bans_banned_id_fkey;
ALTER TABLE user_bans
  ADD CONSTRAINT user_bans_banned_id_fkey
  FOREIGN KEY (banned_id) REFERENCES users(id) ON DELETE CASCADE;

-- direct_conversations — CASCADE both sides (conversation + its DMs vanish on either side's delete).
ALTER TABLE direct_conversations DROP CONSTRAINT direct_conversations_user1_id_fkey;
ALTER TABLE direct_conversations
  ADD CONSTRAINT direct_conversations_user1_id_fkey
  FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE direct_conversations DROP CONSTRAINT direct_conversations_user2_id_fkey;
ALTER TABLE direct_conversations
  ADD CONSTRAINT direct_conversations_user2_id_fkey
  FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE;

-- direct_messages.sender_id — CASCADE (redundant via conversation but symmetric).
ALTER TABLE direct_messages DROP CONSTRAINT direct_messages_sender_id_fkey;
ALTER TABLE direct_messages
  ADD CONSTRAINT direct_messages_sender_id_fkey
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE;

-- room_invitations — CASCADE both sides.
ALTER TABLE room_invitations DROP CONSTRAINT room_invitations_inviter_id_fkey;
ALTER TABLE room_invitations
  ADD CONSTRAINT room_invitations_inviter_id_fkey
  FOREIGN KEY (inviter_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE room_invitations DROP CONSTRAINT room_invitations_invitee_id_fkey;
ALTER TABLE room_invitations
  ADD CONSTRAINT room_invitations_invitee_id_fkey
  FOREIGN KEY (invitee_id) REFERENCES users(id) ON DELETE CASCADE;

-- room_bans — CASCADE both sides.
ALTER TABLE room_bans DROP CONSTRAINT room_bans_banned_user_id_fkey;
ALTER TABLE room_bans
  ADD CONSTRAINT room_bans_banned_user_id_fkey
  FOREIGN KEY (banned_user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE room_bans DROP CONSTRAINT room_bans_banned_by_id_fkey;
ALTER TABLE room_bans
  ADD CONSTRAINT room_bans_banned_by_id_fkey
  FOREIGN KEY (banned_by_id) REFERENCES users(id) ON DELETE CASCADE;

-- messages.deleted_by + direct_messages.deleted_by — SET NULL.
-- (Both columns are already NULLABLE from V5. The V5 migration added these FKs
--  with the default NO ACTION; rewrite them now.)
ALTER TABLE messages DROP CONSTRAINT messages_deleted_by_fkey;
ALTER TABLE messages
  ADD CONSTRAINT messages_deleted_by_fkey
  FOREIGN KEY (deleted_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE direct_messages DROP CONSTRAINT direct_messages_deleted_by_fkey;
ALTER TABLE direct_messages
  ADD CONSTRAINT direct_messages_deleted_by_fkey
  FOREIGN KEY (deleted_by) REFERENCES users(id) ON DELETE SET NULL;

-- message_reactions.user_id + direct_message_reactions.user_id — already CASCADE from V6.
-- messages.reply_to_id + direct_messages.reply_to_id — already SET NULL from V5.
```

- [ ] **Step 2: Verify compile + Flyway applies cleanly**

Rebuild the backend container so Flyway runs V7 against a fresh dev DB:

```bash
cd /src/ai_hakaton && docker compose up -d --build backend
docker logs chat-backend --tail 20
```

Expected: startup log shows `Successfully applied 1 migration to schema "public", now at version v7` (or the existing Flyway fast-forward message). No errors.

If any constraint name doesn't exist in this deployment, Flyway will fail — the error message names the missing constraint. Fix by running `\d+ <table>` in psql to see the real name, edit the migration, rerun.

- [ ] **Step 3: Compile**

```bash
cd /src/ai_hakaton/backend && ./gradlew compileJava compileTestJava
```

Expected: clean. No Java changes yet, migration is pure SQL.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/resources/db/migration/V7__account_management_cascades.sql
git commit -m "database: V7 migration — user-referencing FK cascades for account deletion" -m "chat_rooms.owner_id, room_members.user_id, friendships, user_bans, direct_conversations, direct_messages.sender_id, room_invitations, room_bans all CASCADE on user delete. messages.user_id and deleted_by columns SET NULL so non-owned-room history survives with 'Deleted user' display. Dev-only migration; data wipe is acceptable per user approval." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `UserService.changePassword` + `deleteAccount` + null-author display fallback + tests

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/users/UserService.java`
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageService.java`
- Modify: `backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java`
- Modify: `backend/src/test/java/com/hackathon/features/users/UserServiceTest.java`

- [ ] **Step 1: Add methods to `UserService.java`**

Append inside the existing `UserService` class (keep all current imports and methods):

```java
  private static final int MIN_PASSWORD_LENGTH = 8;

  public static class WrongPasswordException extends RuntimeException {
    public WrongPasswordException() { super("Old password is incorrect"); }
  }

  public void changePassword(UUID userId, String oldPassword, String newPassword) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
      throw new WrongPasswordException();
    }
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }

  public void deleteAccount(UUID userId) {
    if (!userRepository.existsById(userId)) {
      throw new IllegalArgumentException("User not found");
    }
    userRepository.deleteById(userId);
  }
```

- [ ] **Step 2: Update `MessageService.resolveUsername` to handle null user_id**

Find the existing `resolveUsername(UUID userId)` method in `backend/src/main/java/com/hackathon/features/messages/MessageService.java` and replace with:

```java
  private String resolveUsername(UUID userId) {
    if (userId == null) return "Deleted user";
    try {
      User u = userService.getUserById(userId);
      if (u == null) return userId.toString().substring(0, 8);
      return u.getUsername();
    } catch (IllegalArgumentException e) {
      return userId.toString().substring(0, 8);
    }
  }
```

(Adds the `if (userId == null)` guard at the top.)

- [ ] **Step 3: Update `DirectMessageService.resolveUsername` the same way**

Find the existing `resolveUsername(UUID userId)` method in `backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java` and replace with the same body. (Under `direct_messages.sender_id` we have `ON DELETE CASCADE`, so DM messages by a deleted sender are gone — the null path is defensive.)

- [ ] **Step 4: Add `UserService` tests**

Append to `backend/src/test/java/com/hackathon/features/users/UserServiceTest.java`. If the file is Mockito-based, inspect its current shape first — either add new Mockito tests there or split out a new `@SpringBootTest`-style class. For simplicity, match the existing style. Below assumes `@SpringBootTest @ActiveProfiles("test")` style with autowired services (same pattern as `MessageServiceTest`); if the file uses Mockito, translate the calls accordingly.

```java
  @Test
  void changePassword_happyPath_updatesHash() {
    User u = registerUser("a");
    userService.changePassword(u.getId(), "password12345", "newpassword123");
    // authenticateUser should now succeed with the new password and fail with the old one
    assertDoesNotThrow(() -> userService.authenticateUser(u.getEmail(), "newpassword123"));
    assertThrows(IllegalArgumentException.class,
        () -> userService.authenticateUser(u.getEmail(), "password12345"));
  }

  @Test
  void changePassword_wrongOldPassword_throws() {
    User u = registerUser("a");
    assertThrows(UserService.WrongPasswordException.class,
        () -> userService.changePassword(u.getId(), "wrong-old", "newpassword123"));
  }

  @Test
  void changePassword_tooShortNewPassword_throws() {
    User u = registerUser("a");
    assertThrows(IllegalArgumentException.class,
        () -> userService.changePassword(u.getId(), "password12345", "short"));
  }

  @Test
  void deleteAccount_removesUserRow() {
    User u = registerUser("a");
    userService.deleteAccount(u.getId());
    assertThrows(IllegalArgumentException.class, () -> userService.getUserById(u.getId()));
  }

  @Test
  void deleteAccount_missingUser_throws() {
    assertThrows(IllegalArgumentException.class,
        () -> userService.deleteAccount(java.util.UUID.randomUUID()));
  }
```

Ensure the test class has a `registerUser(String suffix)` helper:

```java
  private User registerUser(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }
```

- [ ] **Step 5: Run targeted tests**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'UserServiceTest'
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/users/UserService.java \
        backend/src/main/java/com/hackathon/features/messages/MessageService.java \
        backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java \
        backend/src/test/java/com/hackathon/features/users/UserServiceTest.java
git commit -m "feat(account): UserService changePassword + deleteAccount + null-author display fallback" -m "changePassword: BCrypt old-password check, min 8 chars on new, throws WrongPasswordException for wrong old (mapped to 403 by controller)" -m "deleteAccount: userRepository.deleteById — V7 cascades handle the rest" -m "MessageService + DirectMessageService resolveUsername now returns 'Deleted user' when user_id is null" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `UserController` endpoints + controller tests

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/users/UserController.java`
- Modify: `backend/src/test/java/com/hackathon/features/users/UserControllerTest.java`

- [ ] **Step 1: Add endpoints to `UserController.java`**

Inside the class, keep all existing methods. Add these imports at the top (if not already present):

```java
import com.hackathon.features.users.UserService.WrongPasswordException;
import org.springframework.security.core.Authentication;
```

Add record-style request bodies as inner classes or records near the other request classes:

```java
  public record ChangePasswordRequest(String oldPassword, String newPassword) {}
```

Add a helper (matches the pattern used in `MessageController`/`SearchController`):

```java
  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }
```

Add the two endpoints:

```java
  @PatchMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @RequestBody ChangePasswordRequest request, Authentication authentication) {
    try {
      userService.changePassword(
          currentUserId(authentication), request.oldPassword(), request.newPassword());
      return ResponseEntity.ok().build();
    } catch (WrongPasswordException e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteAccount(Authentication authentication) {
    try {
      userService.deleteAccount(currentUserId(authentication));
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }
```

- [ ] **Step 2: Add controller tests**

Append to `backend/src/test/java/com/hackathon/features/users/UserControllerTest.java`. If the file is Mockito-style, add tests in the same style; if SpringBootTest-style, add SpringBootTest-style tests. Below is the SpringBootTest pattern (matches `MessageControllerTest`); translate if needed.

Required autowirings: `MockMvc mvc`, `UserService userService`, `JwtTokenProvider jwtTokenProvider`, plus a `registerUser(String)` helper.

```java
  @Test
  void changePassword_byAuthor_returnsOk() throws Exception {
    User u = registerUser("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());

    mvc.perform(
            patch("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"oldPassword\":\"password12345\",\"newPassword\":\"newpassword123\"}"))
        .andExpect(status().isOk());

    // Verify the new password works
    assertDoesNotThrow(() -> userService.authenticateUser(u.getEmail(), "newpassword123"));
  }

  @Test
  void changePassword_wrongOld_returns403() throws Exception {
    User u = registerUser("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());

    mvc.perform(
            patch("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"oldPassword\":\"wrong-old\",\"newPassword\":\"newpassword123\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void changePassword_tooShortNew_returns400() throws Exception {
    User u = registerUser("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());

    mvc.perform(
            patch("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"oldPassword\":\"password12345\",\"newPassword\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteAccount_returns204_andUserGone() throws Exception {
    User u = registerUser("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());

    mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    assertThrows(IllegalArgumentException.class, () -> userService.getUserById(u.getId()));
  }

  @Test
  void changePassword_withoutJwt_rejects() throws Exception {
    mvc.perform(
            patch("/api/users/me/password")
                .contentType("application/json")
                .content("{\"oldPassword\":\"x\",\"newPassword\":\"yyyyyyyy\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteAccount_withoutJwt_rejects() throws Exception {
    mvc.perform(delete("/api/users/me")).andExpect(status().isUnauthorized());
  }
```

Import additions the test file needs:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
```

- [ ] **Step 3: Run targeted tests**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'UserControllerTest'
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/users/UserController.java \
        backend/src/test/java/com/hackathon/features/users/UserControllerTest.java
git commit -m "feat(account): PATCH /api/users/me/password + DELETE /api/users/me" -m "- PATCH: 200 on success, 403 on wrong old password (WrongPasswordException), 400 on too-short new" -m "- DELETE: 204 on success; V7 cascades handle all the downstream rows" -m "- 401 from the existing SecurityConfig for unauthenticated callers" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `JwtAuthenticationFilter` existence guard + test

**Files:**
- Modify: `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java`
- Create: `backend/src/test/java/com/hackathon/shared/security/JwtAuthenticationFilterExistenceTest.java`

- [ ] **Step 1: Add existence check to the filter**

Replace `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java` with:

```java
package com.hackathon.shared.security;

import com.hackathon.features.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (jwtTokenProvider.validateToken(token)) {
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        // Guard: the user row may be gone (account deletion). Leave the context
        // unauthenticated so the SecurityConfig entry point returns 401 on protected
        // routes; public routes (/register, /login) continue to work.
        if (userRepository.existsById(userId)) {
          String username = jwtTokenProvider.getUsernameFromToken(token);
          UsernamePasswordAuthenticationToken auth =
              new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
          auth.setDetails(userId);
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    }
    chain.doFilter(request, response);
  }
}
```

- [ ] **Step 2: Create the filter existence test**

Create `backend/src/test/java/com/hackathon/shared/security/JwtAuthenticationFilterExistenceTest.java`:

```java
package com.hackathon.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationFilterExistenceTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void tokenForLiveUser_isAccepted() throws Exception {
    User u = register("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());

    // /api/rooms is an authenticated route that returns a 200 list
    mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void tokenForDeletedUser_is401() throws Exception {
    User u = register("a");
    String token = jwtTokenProvider.generateToken(u.getId(), u.getUsername());
    userService.deleteAccount(u.getId());

    mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }
}
```

- [ ] **Step 3: Run the filter test**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'JwtAuthenticationFilterExistenceTest'
```

Expected: 2 tests pass.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java \
        backend/src/test/java/com/hackathon/shared/security/JwtAuthenticationFilterExistenceTest.java
git commit -m "feat(auth): filter rejects tokens for deleted users with 401" -m "JwtAuthenticationFilter now checks userRepository.existsById before setting the security context. Missing user → context stays unauthenticated → SecurityConfig entry point returns 401." -m "Two-scenario test: live user token works, deleted user token returns 401." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: End-to-end deletion cascade integration test

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/integration/AccountDeletionFlowIntegrationTest.java`

- [ ] **Step 1: Create the test**

```java
package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageRepository;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageRepository;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomRepository;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.rooms.RoomMemberService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountDeletionFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired ChatRoomRepository chatRoomRepository;
  @Autowired RoomMemberService roomMemberService;
  @Autowired MessageService messageService;
  @Autowired MessageRepository messageRepository;
  @Autowired DirectMessageService directMessageService;
  @Autowired DirectMessageRepository directMessageRepository;
  @Autowired ConversationService conversationService;
  @Autowired FriendshipService friendshipService;

  private User register(String suffix) {
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
  void deleteAccount_cascadesOwnedRoomsAndMemberships_preservesBobsUnrelatedData() {
    User alice = register("alice");
    User bob = register("bob");
    makeFriends(alice, bob);

    // Alice owns a room; Bob joins and posts in it
    ChatRoom aliceRoom = chatRoomService.createRoom(
        "alice-" + System.nanoTime(), null, alice.getId(), "public");
    chatRoomService.joinRoom(aliceRoom.getId(), bob.getId());
    Message aliceMsg = messageService.sendMessage(aliceRoom.getId(), alice.getId(), "hi from alice");
    Message bobMsgInAliceRoom =
        messageService.sendMessage(aliceRoom.getId(), bob.getId(), "hi from bob in alice's room");

    // Bob owns his own room; Alice joins and posts
    ChatRoom bobRoom = chatRoomService.createRoom(
        "bob-" + System.nanoTime(), null, bob.getId(), "public");
    chatRoomService.joinRoom(bobRoom.getId(), alice.getId());
    Message aliceMsgInBobRoom =
        messageService.sendMessage(bobRoom.getId(), alice.getId(), "hi from alice in bob's room");

    // Alice + Bob exchange a DM
    DirectConversation conv = conversationService.getOrCreate(alice.getId(), bob.getId());
    DirectMessage dm = directMessageService.send(alice.getId(), conv.getId(), "secret");

    // Act: Alice deletes her account
    userService.deleteAccount(alice.getId());

    // Alice's owned room is gone (cascade)
    assertFalse(chatRoomRepository.existsById(aliceRoom.getId()));
    // Alice's message in her own room is gone (room cascade)
    assertFalse(messageRepository.existsById(aliceMsg.getId()));
    // Bob's message in Alice's room is also gone (the room itself is gone)
    assertFalse(messageRepository.existsById(bobMsgInAliceRoom.getId()));

    // Bob's own room survives
    assertTrue(chatRoomRepository.existsById(bobRoom.getId()));
    // Alice's message in Bob's room survives but with user_id = NULL
    Message surviving = messageRepository.findById(aliceMsgInBobRoom.getId()).orElseThrow();
    assertNull(surviving.getUserId());

    // Alice's membership in Bob's room is gone
    assertFalse(roomMemberService.isMember(bobRoom.getId(), alice.getId()));
    // Bob is still a member of his own room
    assertTrue(roomMemberService.isMember(bobRoom.getId(), bob.getId()));

    // The DM conversation (and its messages) are gone (cascade via user1/user2)
    assertFalse(directMessageRepository.existsById(dm.getId()));
  }

  @Test
  void deletedAuthor_displaysAsDeletedUserInHistory() {
    User alice = register("alice");
    User bob = register("bob");

    ChatRoom bobRoom = chatRoomService.createRoom(
        "bob-" + System.nanoTime(), null, bob.getId(), "public");
    chatRoomService.joinRoom(bobRoom.getId(), alice.getId());
    Message aliceMsg = messageService.sendMessage(bobRoom.getId(), alice.getId(), "hi");

    userService.deleteAccount(alice.getId());

    Message reloaded = messageRepository.findById(aliceMsg.getId()).orElseThrow();
    assertEquals("Deleted user", messageService.toDto(reloaded).getUsername());
  }
}
```

- [ ] **Step 2: Run the integration test**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'AccountDeletionFlowIntegrationTest'
```

Expected: 2 scenarios pass.

- [ ] **Step 3: Run the full backend suite**

```bash
./gradlew test
```

Expected: full suite green.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/test/java/com/hackathon/features/integration/AccountDeletionFlowIntegrationTest.java
git commit -m "test: account deletion cascade flow (owned rooms gone, others preserved)" -m "Scenario 1: Alice owns a room Bob posts in; Bob owns a room Alice posts in; they're friends and have a DM. On Alice's delete, Alice's room + its messages (from both users) are gone, Alice's membership in Bob's room is removed, Alice's own message in Bob's room survives with user_id=NULL, Bob's own room + membership intact, DM conversation + its messages gone." -m "Scenario 2: a message by a deleted author renders as 'Deleted user' via MessageService.toDto." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Frontend `accountService`

**Files:**
- Create: `frontend/src/services/accountService.ts`

- [ ] **Step 1: Create the service**

```typescript
import axios from 'axios';

export const accountService = {
  async changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await axios.patch('/api/users/me/password', { oldPassword, newPassword });
  },

  async deleteAccount(): Promise<void> {
    await axios.delete('/api/users/me');
  },
};
```

- [ ] **Step 2: Build**

```bash
cd /src/ai_hakaton/frontend && npm run build
```

Expected: clean.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/services/accountService.ts
git commit -m "feat(frontend): accountService — changePassword + deleteAccount" -m "Thin axios wrapper over PATCH /api/users/me/password and DELETE /api/users/me." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: `ChangePasswordModal` + `DeleteAccountModal` + ProfileMenu wiring

**Files:**
- Create: `frontend/src/components/ChangePasswordModal.tsx`
- Create: `frontend/src/components/DeleteAccountModal.tsx`
- Modify: `frontend/src/layout/ProfileMenu.tsx`

- [ ] **Step 1: Create `ChangePasswordModal.tsx`**

```tsx
import React, { useState } from 'react';
import { accountService } from '../services/accountService';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

const MIN_LENGTH = 8;

export const ChangePasswordModal: React.FC<Props> = ({ isOpen, onClose }) => {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setOldPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setError(null);
    setBusy(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword.length < MIN_LENGTH) {
      setError(`New password must be at least ${MIN_LENGTH} characters`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('New password and confirmation do not match');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await accountService.changePassword(oldPassword, newPassword);
      close();
    } catch (err) {
      const anyErr = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
      if (anyErr.response?.status === 403) {
        setError('Old password is incorrect');
      } else if (anyErr.response?.status === 400) {
        setError('New password must be at least 8 characters');
      } else {
        setError(anyErr.response?.data?.message ?? anyErr.message ?? 'Password change failed');
      }
      setBusy(false);
    }
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96">
        <h2 className="text-xl font-bold mb-4">Change password</h2>
        <form onSubmit={submit} className="space-y-3">
          <input
            type="password"
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
            placeholder="Current password"
            autoComplete="current-password"
            disabled={busy}
            className="w-full border rounded px-3 py-2"
          />
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="New password (min 8 characters)"
            autoComplete="new-password"
            disabled={busy}
            className="w-full border rounded px-3 py-2"
          />
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="Confirm new password"
            autoComplete="new-password"
            disabled={busy}
            className="w-full border rounded px-3 py-2"
          />
          {error && <div className="text-red-500 text-sm">{error}</div>}
          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={close}
              disabled={busy}
              className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy || !oldPassword || !newPassword || !confirmPassword}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400"
            >
              {busy ? 'Saving…' : 'Change password'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Create `DeleteAccountModal.tsx`**

```tsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { accountService } from '../services/accountService';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

const CONFIRM_PHRASE = 'DELETE';

export const DeleteAccountModal: React.FC<Props> = ({ isOpen, onClose }) => {
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const reset = () => {
    setTyped('');
    setError(null);
    setBusy(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (typed !== CONFIRM_PHRASE) return;
    setBusy(true);
    setError(null);
    try {
      await accountService.deleteAccount();
      localStorage.removeItem('authToken');
      navigate('/login', { replace: true });
    } catch (err) {
      const anyErr = err as { message?: string };
      setError(anyErr.message ?? 'Deletion failed');
      setBusy(false);
    }
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-[28rem]">
        <h2 className="text-xl font-bold mb-2 text-red-600">Delete account</h2>
        <p className="text-sm text-gray-700 mb-3">
          This permanently deletes your account and everything you own:
        </p>
        <ul className="text-sm text-gray-700 mb-4 list-disc pl-5 space-y-1">
          <li>Every chat room you own (with all its messages)</li>
          <li>Your memberships in other rooms</li>
          <li>Your friendships, direct-message conversations, and bans</li>
          <li>Reactions and invitations you sent or received</li>
        </ul>
        <p className="text-sm text-gray-700 mb-3">
          Messages you sent in other people's rooms stay, but your name becomes
          "Deleted user".
        </p>
        <form onSubmit={submit} className="space-y-3">
          <label className="block text-sm font-medium">
            Type <code className="bg-gray-100 px-1 rounded">{CONFIRM_PHRASE}</code> to confirm:
          </label>
          <input
            type="text"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            disabled={busy}
            autoComplete="off"
            className="w-full border rounded px-3 py-2"
          />
          {error && <div className="text-red-500 text-sm">{error}</div>}
          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={close}
              disabled={busy}
              className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy || typed !== CONFIRM_PHRASE}
              className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 disabled:bg-gray-400"
            >
              {busy ? 'Deleting…' : 'Delete account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
```

- [ ] **Step 3: Wire into `ProfileMenu.tsx`**

Replace `frontend/src/layout/ProfileMenu.tsx` with:

```tsx
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChangePasswordModal } from '../components/ChangePasswordModal';
import { DeleteAccountModal } from '../components/DeleteAccountModal';

interface Props {
  username: string;
}

export const ProfileMenu: React.FC<Props> = ({ username }) => {
  const [open, setOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const signOut = () => {
    localStorage.removeItem('authToken');
    navigate('/login', { replace: true });
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex items-center gap-1 px-3 py-2 rounded hover:bg-gray-100"
      >
        {username} <span aria-hidden>▼</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 mt-1 w-48 bg-white border rounded shadow z-50">
          <div className="px-3 py-2 text-xs text-gray-500 border-b">Signed in as</div>
          <div className="px-3 py-2 text-sm truncate">{username}</div>
          <button
            onClick={() => {
              setOpen(false);
              setPasswordOpen(true);
            }}
            className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100"
            role="menuitem"
          >
            Change password
          </button>
          <button
            onClick={() => {
              setOpen(false);
              setDeleteOpen(true);
            }}
            className="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50"
            role="menuitem"
          >
            Delete account
          </button>
          <div className="border-t" />
          <button
            onClick={signOut}
            className="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50"
            role="menuitem"
          >
            Sign out
          </button>
        </div>
      )}

      <ChangePasswordModal isOpen={passwordOpen} onClose={() => setPasswordOpen(false)} />
      <DeleteAccountModal isOpen={deleteOpen} onClose={() => setDeleteOpen(false)} />
    </div>
  );
};
```

- [ ] **Step 4: Build + vitest**

```bash
cd /src/ai_hakaton/frontend && npm run build && npm test -- --run
```

Expected: clean + green.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/components/ChangePasswordModal.tsx \
        frontend/src/components/DeleteAccountModal.tsx \
        frontend/src/layout/ProfileMenu.tsx
git commit -m "feat(frontend): account management modals wired into ProfileMenu" -m "- ChangePasswordModal: old + new + confirm; maps 403 → 'Old password is incorrect', 400 → 'New password must be at least 8 characters'" -m "- DeleteAccountModal: warning copy + 'Type DELETE to confirm' input; on success clears token and navigates to /login" -m "- ProfileMenu gains two menu items and renders the two modals" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Playwright E2E — account-management.spec.ts

**Files:**
- Create: `frontend/e2e/account-management.spec.ts`

- [ ] **Step 1: Rebuild the backend container so the new endpoints + V7 are live**

```bash
cd /src/ai_hakaton && docker compose up -d --build backend
docker logs chat-backend --tail 5
```

Wait for "Started ChatServerApplication".

- [ ] **Step 2: Create the spec**

```typescript
import { test, expect, Browser, Page } from '@playwright/test';

const password = 'password12345';

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

test.describe('Account management', () => {
  test('change password → log out → log in with new password', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const { ctx, page } = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );

    // Open profile menu and click "Change password"
    await page.getByRole('button', { name: new RegExp(alice.username) }).click();
    await page.getByRole('menuitem', { name: 'Change password' }).click();

    await page.fill('input[placeholder="Current password"]', alice.password);
    const newPassword = 'newpassword123';
    await page.fill('input[placeholder^="New password"]', newPassword);
    await page.fill('input[placeholder="Confirm new password"]', newPassword);
    await page.click('button:has-text("Change password"):not(:has-text("Cancel"))');

    // Modal closes on success; log out
    await page.getByRole('button', { name: new RegExp(alice.username) }).click();
    await page.getByRole('menuitem', { name: 'Sign out' }).click();
    await page.waitForURL(/.*\/login$/);

    // Log in with new password
    await page.fill('#email', alice.email);
    await page.fill('#password', newPassword);
    await page.click('button[type="submit"]');
    await page.waitForURL(/.*\/rooms$/);

    await ctx.close();
  });

  test('delete account → redirected to /login → same email re-registers', async ({ browser }) => {
    const bob = uniqueUser('bob');
    const { ctx, page } = await registerAndLogin(
      browser,
      bob.email,
      bob.username,
      bob.password,
    );

    await page.getByRole('button', { name: new RegExp(bob.username) }).click();
    await page.getByRole('menuitem', { name: 'Delete account' }).click();

    await page.fill('input[autocomplete="off"]', 'DELETE');
    await page.click('button:has-text("Delete account"):not(:has-text("Cancel"))');
    await page.waitForURL(/.*\/login$/, { timeout: 10_000 });

    // Re-register with the same email to confirm the account is truly gone
    await page.goto('/register');
    await page.fill('#email', bob.email);
    await page.fill('#username', bob.username);
    await page.fill('#password', bob.password);
    await page.fill('#confirm-password', bob.password);
    await page.click('button[type="submit"]');
    await page.waitForURL(/.*\/login$/);

    await ctx.close();
  });
});
```

- [ ] **Step 3: Run the full Playwright suite**

```bash
cd /src/ai_hakaton/frontend && npm run test:e2e -- --reporter=line
```

Expected: full suite green, including the 2 new scenarios.

If a selector in the new spec is wrong (e.g. the "Change password" submit button has additional whitespace), adjust only inside this spec file — do not touch production code.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add frontend/e2e/account-management.spec.ts
git commit -m "test(e2e): account management — change password + delete account" -m "Alice registers, changes password via the profile dropdown, signs out, signs back in with the new password." -m "Bob registers, deletes his account via the profile dropdown (typing DELETE to confirm), is redirected to /login, and the same email is reusable for a fresh registration (confirming cascade removed the user row)." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: FEATURES_ROADMAP.md update

**Files:**
- Modify: `FEATURES_ROADMAP.md`

- [ ] **Step 1: Move Feature #8 from Planned to Completed**

Find the `### Feature #8: Account Management` entry under `## Planned Features` and replace with:

```markdown
### Feature #8: Account Management ✅
- Password change for logged-in users — `PATCH /api/users/me/password` requires old password and min-8-char new password (403 on wrong old, 400 on too short)
- Account deletion — `DELETE /api/users/me` permanently removes the user row; V7 migration cascades through owned rooms + memberships + friendships + DMs + invitations + bans + reactions
- Messages sent in rooms the deleted user did not own survive with `user_id = NULL` and render as "Deleted user" (backend service fallback)
- `JwtAuthenticationFilter` rejects tokens for deleted users with 401 on the next request
- Frontend: `ChangePasswordModal` and `DeleteAccountModal` wired into the profile dropdown; "Type DELETE to confirm" gate on deletion
- Backend tests: `UserServiceTest`, `UserControllerTest`, `JwtAuthenticationFilterExistenceTest`, `AccountDeletionFlowIntegrationTest`
- Playwright: `account-management.spec.ts` — change-then-login + delete-then-reregister
- Spec: `docs/superpowers/specs/2026-04-19-account-management-design.md`
- Plan: `docs/superpowers/plans/2026-04-19-account-management.md` (9 tasks — all complete)
- **Status: COMPLETE**
```

And move it into `## Completed Features` (below the emoji/reactions polish entry).

- [ ] **Step 2: Update the Progress block**

Replace:

```markdown
## Progress
- **Completed:** 6 execution slots (Features #1, #2, #3, #4, App Shell Refactor, Message Content) + polish (emoji picker + reactions, chat ordering)
- **In progress:** 0
- **Remaining:** 3 (Attachments, Presence/Sessions, Account Management)
```

with:

```markdown
## Progress
- **Completed:** 7 execution slots (Features #1, #2, #3, #4, App Shell Refactor, Message Content, Account Management) + polish (emoji picker + reactions, chat ordering)
- **In progress:** 0
- **Remaining:** 3 (Attachments, Presence/Sessions, Password Reset)
```

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add FEATURES_ROADMAP.md
git commit -m "docs(roadmap): Feature #8 (account management) complete" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Verification Checklist

Before considering Feature #8 shipped:

- [ ] V7 migration applies cleanly on a fresh container (`docker compose up -d --build backend` → "Successfully applied" in logs)
- [ ] `./gradlew test` — full backend suite green
- [ ] `npm run build` — clean
- [ ] `npm test -- --run` — vitest green
- [ ] `npm run test:e2e` — Playwright green including `account-management.spec.ts`
- [ ] Browser smoke:
  - Change password via profile dropdown; sign out; sign back in with new password
  - Delete account; confirm redirect to `/login`; same email re-registers
  - Reuse an old JWT after delete → request 401
- [ ] `FEATURES_ROADMAP.md` reflects Feature #8 COMPLETE; Feature #9 (Password Reset) remains TODO
