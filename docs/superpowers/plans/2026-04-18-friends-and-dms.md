# Feature #3: Friends & Direct Messaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a contacts/friends system and one-to-one direct messaging on top of Feature #2, including a schema-and-auth retrofit that fixes the User→Integer/UUID inconsistency left by Feature #2.

**Architecture:** Separate tables per the approved spec — `friendships`, `user_bans`, `direct_conversations`, `direct_messages`. Migration `V3__friends_and_dms.sql` recreates the `users` table with UUID IDs, adds FK constraints from Feature #2 tables to `users`, creates the 4 new tables, and converts every `TIMESTAMP` column in the database to `TIMESTAMPTZ`. Spring WebSocket user destinations (`/user/queue/dms`, `/user/queue/friend-events`) deliver real-time events to specific sessions. A new `JwtAuthenticationFilter` populates the Spring `SecurityContext` from the Bearer token so controllers can resolve the authenticated user.

**Tech Stack:** Spring Boot 3.5.12 on Java 25, Gradle 9.4.1, PostgreSQL 15, Flyway, Spring Security + JWT (jjwt 0.12.3), Spring WebSocket / STOMP. JPA with Lombok `@Data @Builder` entities. Tests: JUnit 5, Mockito, Spring Boot Test, Testcontainers PostgreSQL, Instancio. Frontend: React 19 + TypeScript + Vite, Vitest, axios, @stomp/stompjs + sockjs-client. Reference spec: `docs/superpowers/specs/2026-04-18-friends-and-dms-design.md`.

---

## File Structure

### Backend — new packages/files

```
backend/src/main/java/com/hackathon/
  features/friendships/
    Friendship.java
    FriendshipRepository.java
    FriendshipService.java
    FriendshipController.java
  features/bans/
    UserBan.java
    UserBanRepository.java
    UserBanService.java
    UserBanController.java
  features/dms/
    DirectConversation.java
    DirectConversationRepository.java
    DirectMessage.java
    DirectMessageRepository.java
    ConversationService.java
    DirectMessageService.java
    DirectMessageController.java
  shared/security/
    JwtAuthenticationFilter.java       (new)
    SecurityConfig.java                (replace body)
  shared/websocket/
    DirectMessageHandler.java          (new)
    FriendshipEventPublisher.java      (new — called from services)
backend/src/main/resources/db/migration/
  V3__friends_and_dms.sql              (new)
backend/src/test/java/com/hackathon/
  features/friendships/
    FriendshipServiceTest.java
    FriendshipControllerTest.java
  features/bans/
    UserBanServiceTest.java
    UserBanControllerTest.java
  features/dms/
    ConversationServiceTest.java
    DirectMessageServiceTest.java
    DirectMessageControllerTest.java
  shared/websocket/
    DirectMessageHandlerTest.java
  integration/
    FriendsAndDmsFlowIntegrationTest.java
```

### Backend — modified files

```
backend/src/main/java/com/hackathon/features/users/
  User.java                          (Integer id → UUID; LocalDateTime → OffsetDateTime)
  UserRepository.java                (JpaRepository<User, Integer> → <User, UUID>)
  UserService.java                   (getUserById Integer → UUID; add findByUsername exposing entity)
  UserController.java                (UserResponse.id Integer → UUID)
backend/src/main/java/com/hackathon/features/rooms/
  ChatRoom.java                      (LocalDateTime → OffsetDateTime)
  RoomMember.java                    (LocalDateTime → OffsetDateTime)
  ChatRoomController.java            (remove placeholder UUID, use authenticated user)
backend/src/main/java/com/hackathon/features/messages/
  Message.java                       (LocalDateTime → OffsetDateTime)
  MessageController.java             (remove placeholder UUID, use authenticated user)
backend/src/main/java/com/hackathon/shared/security/
  JwtTokenProvider.java              (Integer → UUID in generate/extract)
backend/src/main/java/com/hackathon/shared/websocket/
  ChatMessageHandler.java            (remove placeholder UUID, use authenticated user)
  ChatMessageDTO.java                (LocalDateTime → OffsetDateTime)
backend/src/test/java/com/hackathon/features/users/
  UserServiceTest.java               (Integer → UUID)
  UserControllerTest.java            (Integer → UUID)
backend/src/test/java/com/hackathon/features/rooms/
  ChatRoomControllerTest.java        (stub UserService.findByUsername)
backend/src/test/java/com/hackathon/features/messages/
  MessageControllerTest.java         (stub UserService.findByUsername)
```

### Frontend — new files

```
frontend/src/types/
  friendship.ts
  ban.ts
  directMessage.ts
frontend/src/services/
  friendshipService.ts
  banService.ts
  directMessageService.ts
frontend/src/hooks/
  useFriends.ts
  useFriendRequests.ts
  useDirectConversations.ts
  useDirectMessages.ts
  useDirectMessageSocket.ts
frontend/src/components/
  AppSidebar.tsx
  FriendsList.tsx
  FriendRequestList.tsx
  SendFriendRequestForm.tsx
  ConversationList.tsx
  RoomMembersPanel.tsx
frontend/src/pages/
  FriendsPage.tsx
  DirectMessagesPage.tsx
  DirectChatPage.tsx
frontend/src/__tests__/
  FriendsAndDmsFlow.test.tsx
```

### Frontend — modified files

```
frontend/src/App.tsx                 (new routes, wrap with AppSidebar)
frontend/src/pages/ChatPage.tsx      (add RoomMembersPanel)
frontend/src/services/websocketService.ts (add subscribeUserQueue helper if needed)
```

---

## Task Overview

| # | Task | Scope |
|---|------|-------|
| 1 | V3 migration | Schema retrofit + 4 new tables + TIMESTAMPTZ |
| 2 | User entity → UUID + OffsetDateTime | Break compile, re-establish |
| 3 | UserService/Controller → UUID | Keep UserController API consistent |
| 4 | JwtTokenProvider → UUID | Token subject becomes UUID string |
| 5 | JwtAuthenticationFilter + SecurityConfig | Real auth populates SecurityContext |
| 6 | Retrofit Feature #2 entities + controllers | OffsetDateTime + real user ID |
| 7 | Friendship entity + repository | JPA setup |
| 8 | FriendshipService TDD | Send/accept/reject/list/remove; auto-accept on inverse |
| 9 | FriendshipController + tests | REST endpoints |
| 10 | UserBan package | Entity + repo + service + controller + tests |
| 11 | DM entities + repositories | Conversation + message JPA |
| 12 | ConversationService + tests | Canonical-ordering get-or-create |
| 13 | DirectMessageService + tests | Send validation + cursor pagination |
| 14 | DirectMessageController + tests | REST endpoints |
| 15 | WebSocket: DirectMessageHandler + FriendshipEventPublisher | Real-time delivery |
| 16 | Frontend types + services | friendship, ban, directMessage |
| 17 | Frontend hooks | useFriends, requests, conversations, messages, socket |
| 18 | AppSidebar + routing | Persistent left nav |
| 19 | FriendsPage + components | Friends list + request list + send form |
| 20 | DirectMessagesPage + ConversationList | Conversation list with previews |
| 21 | DirectChatPage | DM chat view reusing MessageList/MessageInput |
| 22 | RoomMembersPanel + ChatPage update | "Add Friend" from room user list |
| 23 | Backend integration test | Friends + DMs end-to-end |
| 24 | Frontend integration test | Full UI flow |

---

## Section A: Schema & Auth Retrofit

### Task 1: V3 Flyway Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__friends_and_dms.sql`

- [ ] **Step 1: Write migration SQL**

Create `backend/src/main/resources/db/migration/V3__friends_and_dms.sql` with **exact** content:

```sql
-- 1. Drop users table (no prod data) and recreate with UUID PK.
--    Cascade drops any FK-pointing columns, but Feature #2 tables only had
--    UUID user_id columns WITHOUT FK constraints, so they survive untouched.
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Add FK constraints on Feature #2 tables now that users.id is UUID.
--    Remove any orphan rows first (hackathon dev DB — safe to truncate).
TRUNCATE TABLE messages, room_members, chat_rooms CASCADE;

ALTER TABLE chat_rooms
    ADD CONSTRAINT fk_chat_rooms_owner FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE chat_rooms
    ADD CONSTRAINT chat_rooms_visibility_check CHECK (visibility IN ('public', 'private'));

ALTER TABLE room_members
    ADD CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users(id);

-- 3. Convert existing TIMESTAMP columns to TIMESTAMPTZ (UTC interpretation).
ALTER TABLE chat_rooms
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE chat_rooms
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE room_members
    ALTER COLUMN joined_at TYPE TIMESTAMPTZ USING joined_at AT TIME ZONE 'UTC';

ALTER TABLE room_members
    ALTER COLUMN joined_at SET DEFAULT now();

ALTER TABLE messages
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE messages
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

-- 4. New Feature #3 tables.

CREATE TABLE friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id),
    addressee_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'accepted')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friendship_pair UNIQUE (requester_id, addressee_id),
    CONSTRAINT no_self_friendship CHECK (requester_id <> addressee_id)
);

CREATE INDEX idx_friendships_addressee_status ON friendships(addressee_id, status);
CREATE INDEX idx_friendships_requester_status ON friendships(requester_id, status);

CREATE TABLE user_bans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    banner_id UUID NOT NULL REFERENCES users(id),
    banned_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_ban_pair UNIQUE (banner_id, banned_id),
    CONSTRAINT no_self_ban CHECK (banner_id <> banned_id)
);

CREATE INDEX idx_user_bans_banned ON user_bans(banned_id);

CREATE TABLE direct_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id UUID NOT NULL REFERENCES users(id),
    user2_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_direct_conv_pair UNIQUE (user1_id, user2_id),
    CONSTRAINT canonical_user_order CHECK (user1_id < user2_id)
);

CREATE TABLE direct_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES direct_conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    text VARCHAR(3072) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_direct_messages_conv_created ON direct_messages(conversation_id, created_at DESC);
```

- [ ] **Step 2: Verify migration parses (Flyway dry-check via startup)**

After Task 2 (next task) is complete you'll run `./gradlew bootRun` to apply all migrations. For this task, verify the SQL is syntactically valid:

```bash
cd /src/ai_hakaton
docker exec -i chat-postgres psql -U postgres -d app_db -f - < backend/src/main/resources/db/migration/V3__friends_and_dms.sql > /tmp/v3-check.log 2>&1; echo "exit=$?"
# If docker isn't running, skip this check — Task 2's integration test will validate.
```

Expected: exit=0 (or "docker not running" which is acceptable; migration runs in Task 6 end-to-end verification).

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/resources/db/migration/V3__friends_and_dms.sql
git commit -m "database: V3 migration for friends, DMs, UUID users, TIMESTAMPTZ

- Drop users table and recreate with UUID primary key (no prod data)
- Add FK constraints from chat_rooms/room_members/messages to users(id)
- Add visibility CHECK constraint on chat_rooms
- Convert all existing TIMESTAMP columns to TIMESTAMPTZ (UTC)
- Create friendships table (requester+addressee+status)
- Create user_bans table (unidirectional, no self-ban)
- Create direct_conversations (canonical user ordering)
- Create direct_messages with cursor-pagination index"
```

---

### Task 2: User Entity & Repository → UUID + OffsetDateTime

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/users/User.java`
- Modify: `backend/src/main/java/com/hackathon/features/users/UserRepository.java`

- [ ] **Step 1: Replace User entity**

Replace entire contents of `backend/src/main/java/com/hackathon/features/users/User.java`:

```java
package com.hackathon.features.users;

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
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(unique = true, nullable = false)
  private String username;

  @Column(unique = true, nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
```

- [ ] **Step 2: Replace UserRepository**

Replace entire contents of `backend/src/main/java/com/hackathon/features/users/UserRepository.java`:

```java
package com.hackathon.features.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);
}
```

- [ ] **Step 3: Verify compile fails in expected places**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava 2>&1 | tail -30
```

Expected: compile errors referencing `Integer` in `UserService`, `UserController`, `JwtTokenProvider`. Those are fixed in Tasks 3–4.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/users/User.java backend/src/main/java/com/hackathon/features/users/UserRepository.java
git commit -m "refactor(users): switch User to UUID PK and OffsetDateTime timestamps"
```

---

### Task 3: UserService + UserController → UUID

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/users/UserService.java`
- Modify: `backend/src/main/java/com/hackathon/features/users/UserController.java`
- Modify: `backend/src/test/java/com/hackathon/features/users/UserServiceTest.java`
- Modify: `backend/src/test/java/com/hackathon/features/users/UserControllerTest.java`

- [ ] **Step 1: Replace UserService**

Replace entire contents of `backend/src/main/java/com/hackathon/features/users/UserService.java`:

```java
package com.hackathon.features.users;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public User registerUser(String email, String username, String password) {
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already exists");
    }
    if (userRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists");
    }

    User user =
        User.builder()
            .email(email)
            .username(username)
            .passwordHash(passwordEncoder.encode(password))
            .build();

    return userRepository.save(user);
  }

  public User authenticateUser(String email, String password) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid password");
    }

    return user;
  }

  public User getUserById(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  public User getUserByUsername(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }
}
```

- [ ] **Step 2: Replace UserController**

Replace entire contents of `backend/src/main/java/com/hackathon/features/users/UserController.java`:

```java
package com.hackathon.features.users;

import com.hackathon.shared.security.JwtTokenProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
  private final UserService userService;
  private final JwtTokenProvider jwtTokenProvider;

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
    try {
      User user =
          userService.registerUser(request.getEmail(), request.getUsername(), request.getPassword());
      return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(user));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    try {
      User user = userService.authenticateUser(request.getEmail(), request.getPassword());
      String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
      return ResponseEntity.ok(new LoginResponse(token, new UserResponse(user)));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponse> getCurrentUser(
      @RequestHeader("Authorization") String authHeader) {
    try {
      String token = authHeader.replace("Bearer ", "");
      if (!jwtTokenProvider.validateToken(token)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      UUID userId = jwtTokenProvider.getUserIdFromToken(token);
      User user = userService.getUserById(userId);
      return ResponseEntity.ok(new UserResponse(user));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
    return ResponseEntity.ok().build();
  }

  public static class RegisterRequest {
    private String email;
    private String username;
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
  }

  public static class LoginRequest {
    private String email;
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
  }

  public static class UserResponse {
    private UUID id;
    private String email;
    private String username;

    public UserResponse(User user) {
      this.id = user.getId();
      this.email = user.getEmail();
      this.username = user.getUsername();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
  }

  public static class LoginResponse {
    private String token;
    private UserResponse user;

    public LoginResponse(String token, UserResponse user) {
      this.token = token;
      this.user = user;
    }

    public String getToken() { return token; }
    public UserResponse getUser() { return user; }
  }
}
```

- [ ] **Step 3: Update UserServiceTest for UUID**

Open `backend/src/test/java/com/hackathon/features/users/UserServiceTest.java`. Replace every `Integer` used as a user ID with `UUID`, and replace literal ints (`1`, `2`) with `UUID.randomUUID()`. Specifically:

- Add `import java.util.UUID;`
- Any declaration like `Integer userId = 1;` → `UUID userId = UUID.randomUUID();`
- Any `.id(1)` in a `User.builder()` → `.id(UUID.randomUUID())`
- Any `verify(userRepository).findById(1)` → capture the UUID used above and verify with the same value

If you are unsure which lines to change, run:

```bash
cd /src/ai_hakaton
grep -n "Integer\| 1\| 2" backend/src/test/java/com/hackathon/features/users/UserServiceTest.java
```

and change each occurrence to the UUID equivalent.

- [ ] **Step 4: Update UserControllerTest for UUID**

Same treatment for `backend/src/test/java/com/hackathon/features/users/UserControllerTest.java`:

- Add `import java.util.UUID;`
- Replace Integer user IDs with UUIDs
- Any `jwtTokenProvider.generateToken(1, "...")` → `jwtTokenProvider.generateToken(UUID.fromString("..."), "...")` with a fixed UUID
- Any `getUserIdFromToken(anyString())` that returns Integer → return `UUID.randomUUID()`

- [ ] **Step 5: Run user tests**

```bash
cd /src/ai_hakaton/backend
./gradlew test --tests 'UserServiceTest' --tests 'UserControllerTest'
```

Expected: compilation may still fail due to `JwtTokenProvider` returning `Integer` — that's fixed in Task 4. If compilation fails only inside `JwtTokenProvider`, proceed to Task 4. If any other test errors, fix them now.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/users/UserService.java backend/src/main/java/com/hackathon/features/users/UserController.java backend/src/test/java/com/hackathon/features/users/UserServiceTest.java backend/src/test/java/com/hackathon/features/users/UserControllerTest.java
git commit -m "refactor(users): UserService/Controller use UUID; expose getUserByUsername"
```

---

### Task 4: JwtTokenProvider → UUID

**Files:**
- Modify: `backend/src/main/java/com/hackathon/shared/security/JwtTokenProvider.java`

- [ ] **Step 1: Replace JwtTokenProvider**

Replace entire contents of `backend/src/main/java/com/hackathon/shared/security/JwtTokenProvider.java`:

```java
package com.hackathon.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
  @Value("${jwt.secret:my-secret-key-that-is-at-least-32-characters-long-for-HS256}")
  private String jwtSecret;

  @Value("${jwt.expiration:86400000}")
  private long jwtExpiration;

  public String generateToken(UUID userId, String username) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder()
        .subject(userId.toString())
        .claim("username", username)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public UUID getUserIdFromToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return UUID.fromString(
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject());
  }

  public String getUsernameFromToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .get("username", String.class);
  }

  public boolean validateToken(String token) {
    try {
      SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
```

**Notes:**
- Claim changed from `"email"` to `"username"` — username is what Spring `Authentication.getName()` will return after Task 5.
- The existing `UserController.login` call `jwtTokenProvider.generateToken(user.getId(), user.getUsername())` (set in Task 3) now matches this signature.

- [ ] **Step 2: Run compile**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava
```

Expected: compilation succeeds if Feature #2 controllers have not yet been retrofitted; they do not reference `getUserIdFromToken` directly. If there are still errors, they'll come from Feature #2 controllers using `UUID.randomUUID()` which is still valid — those stay as-is until Task 6.

- [ ] **Step 3: Run user tests**

```bash
./gradlew test --tests 'UserServiceTest' --tests 'UserControllerTest'
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/shared/security/JwtTokenProvider.java
git commit -m "refactor(jwt): token subject is UUID string; claim is username not email"
```

---

### Task 5: JwtAuthenticationFilter + SecurityConfig

**Files:**
- Create: `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java`

- [ ] **Step 1: Create JwtAuthenticationFilter**

Create `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java`:

```java
package com.hackathon.shared.security;

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

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (jwtTokenProvider.validateToken(token)) {
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }
    chain.doFilter(request, response);
  }
}
```

Principal = username. `auth.getDetails()` = UUID userId. Controllers can read both from `Authentication`.

- [ ] **Step 2: Replace SecurityConfig**

Replace entire contents of `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java`:

```java
package com.hackathon.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/users/register", "/api/users/login")
                    .permitAll()
                    .requestMatchers("/ws/chat/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
```

- [ ] **Step 3: Add test security profile (avoid JWT filter in @WithMockUser tests)**

Check if `backend/src/test/java/com/hackathon/shared/security/TestSecurityConfig.java` exists. If yes, skip. If no, create it:

```java
package com.hackathon.shared.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
public class TestSecurityConfig {
  @Bean
  @Primary
  public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
```

Existing controller tests that already use `.with(csrf())` will continue to work because CSRF is disabled; `@WithMockUser` sets the SecurityContext directly.

- [ ] **Step 4: Full compile + test**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava compileTestJava
```

Expected: success.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter.java backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java backend/src/test/java/com/hackathon/shared/security/TestSecurityConfig.java
git commit -m "feat(security): JwtAuthenticationFilter + stateless SecurityFilterChain

- Filter validates Bearer tokens, populates SecurityContext with username principal and UUID userId in details
- Public endpoints: /api/users/register, /api/users/login, /ws/chat/**
- Everything else requires authentication
- CSRF disabled (JWT auth, no session cookies)
- Test security config permits all for @WithMockUser-based tests"
```

---

### Task 6: Retrofit Feature #2 entities + controllers for OffsetDateTime + real user ID

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoom.java`
- Modify: `backend/src/main/java/com/hackathon/features/rooms/RoomMember.java`
- Modify: `backend/src/main/java/com/hackathon/features/messages/Message.java`
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java`
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageController.java`
- Modify: `backend/src/main/java/com/hackathon/shared/websocket/ChatMessageHandler.java`
- Modify: `backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java`

- [ ] **Step 1: Update ChatRoom timestamps**

In `backend/src/main/java/com/hackathon/features/rooms/ChatRoom.java`:

- Replace import `import java.time.LocalDateTime;` with `import java.time.OffsetDateTime;`
- Replace both `private LocalDateTime createdAt;` and `private LocalDateTime updatedAt;` declarations so the type is `OffsetDateTime`.

- [ ] **Step 2: Update RoomMember timestamps**

In `backend/src/main/java/com/hackathon/features/rooms/RoomMember.java`:

- Replace `LocalDateTime` with `OffsetDateTime` in the import and field declarations (same approach as Step 1).

- [ ] **Step 3: Update Message timestamps**

In `backend/src/main/java/com/hackathon/features/messages/Message.java`:

- Replace `LocalDateTime` with `OffsetDateTime` in the import and field declarations.

- [ ] **Step 4: Update ChatMessageDTO timestamps**

In `backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java`:

- Replace `LocalDateTime` with `OffsetDateTime` in the import and the `createdAt` field declaration.

- [ ] **Step 5: Replace ChatRoomController**

Replace entire contents of `backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java`:

```java
package com.hackathon.features.rooms;

import com.hackathon.features.users.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {
  private final ChatRoomService chatRoomService;
  private final UserService userService;

  record CreateRoomRequest(String name, String description) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) {
      return uuid;
    }
    // Fallback: look up by username (tests using @WithMockUser hit this path)
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping
  public ResponseEntity<ChatRoom> createRoom(
      @RequestBody CreateRoomRequest request, Authentication authentication) {
    ChatRoom room =
        chatRoomService.createRoom(request.name(), request.description(), currentUserId(authentication));
    return ResponseEntity.ok(room);
  }

  @GetMapping
  public ResponseEntity<Page<ChatRoom>> listPublicRooms(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(chatRoomService.listPublicRooms(page, limit));
  }

  @PostMapping("/{id}/join")
  public ResponseEntity<Void> joinRoom(@PathVariable UUID id, Authentication authentication) {
    chatRoomService.joinRoom(id, currentUserId(authentication));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/leave")
  public ResponseEntity<Void> leaveRoom(@PathVariable UUID id, Authentication authentication) {
    chatRoomService.leaveRoom(id, currentUserId(authentication));
    return ResponseEntity.ok().build();
  }
}
```

- [ ] **Step 6: Replace MessageController**

Replace entire contents of `backend/src/main/java/com/hackathon/features/messages/MessageController.java`:

```java
package com.hackathon.features.messages;

import com.hackathon.features.users.UserService;
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

  record SendMessageRequest(String text) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) {
      return uuid;
    }
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping
  public ResponseEntity<List<Message>> getMessageHistory(
      @PathVariable UUID roomId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(messageService.getMessageHistory(roomId, before, limit));
  }

  @PostMapping
  public ResponseEntity<Message> sendMessage(
      @PathVariable UUID roomId,
      @RequestBody SendMessageRequest request,
      Authentication authentication) {
    Message message =
        messageService.sendMessage(roomId, currentUserId(authentication), request.text());
    return ResponseEntity.ok(message);
  }
}
```

- [ ] **Step 7: Replace ChatMessageHandler**

Replace entire contents of `backend/src/main/java/com/hackathon/shared/websocket/ChatMessageHandler.java`:

```java
package com.hackathon.shared.websocket;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {
  private final MessageService messageService;
  private final UserService userService;

  @MessageMapping("/rooms/{roomId}/message")
  @SendTo("/topic/room/{roomId}")
  public ChatMessageDTO handleMessage(
      ChatMessageDTO payload, @DestinationVariable UUID roomId, Principal principal) {
    var user = userService.getUserByUsername(principal.getName());
    Message saved = messageService.sendMessage(roomId, user.getId(), payload.getText());
    return ChatMessageDTO.builder()
        .id(saved.getId())
        .roomId(saved.getRoomId())
        .userId(saved.getUserId())
        .username(user.getUsername())
        .text(saved.getText())
        .createdAt(saved.getCreatedAt())
        .build();
  }
}
```

- [ ] **Step 8: Update existing controller tests**

Add `@MockBean private com.hackathon.features.users.UserService userService;` to both `ChatRoomControllerTest` and `MessageControllerTest`, and in each test's setup stub:

```java
com.hackathon.features.users.User mockUser =
    com.hackathon.features.users.User.builder().id(UUID.randomUUID()).username("user").build();
when(userService.getUserByUsername("user")).thenReturn(mockUser);
```

Put that stubbing in a `@BeforeEach` or at the start of each test that calls a controller endpoint.

- [ ] **Step 9: Run full test suite end-to-end**

Ensure Postgres is running (`docker compose up -d postgres` if not), then:

```bash
cd /src/ai_hakaton/backend
./gradlew test
```

Expected: all tests PASS. Flyway applies V1–V3 successfully.

- [ ] **Step 10: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/ChatRoom.java backend/src/main/java/com/hackathon/features/rooms/RoomMember.java backend/src/main/java/com/hackathon/features/messages/Message.java backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java backend/src/main/java/com/hackathon/features/messages/MessageController.java backend/src/main/java/com/hackathon/shared/websocket/ChatMessageHandler.java backend/src/test/java/com/hackathon/features/rooms/ChatRoomControllerTest.java backend/src/test/java/com/hackathon/features/messages/MessageControllerTest.java
git commit -m "refactor(rooms,messages): OffsetDateTime timestamps + resolve real user ID from auth

- Entities switch LocalDateTime to OffsetDateTime (matches TIMESTAMPTZ)
- ChatRoomController/MessageController/ChatMessageHandler now resolve the authenticated
  user via Authentication details (UUID) or UserService.getUserByUsername fallback
- Tests stub UserService.getUserByUsername to return a user for @WithMockUser 'user'"
```

---

## Section B: Friendships

### Task 7: Friendship entity + repository

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/friendships/Friendship.java`
- Create: `backend/src/main/java/com/hackathon/features/friendships/FriendshipRepository.java`

- [ ] **Step 1: Create Friendship entity**

```java
package com.hackathon.features.friendships;

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
@Table(name = "friendships",
    uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Friendship {
  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_ACCEPTED = "accepted";

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "requester_id", nullable = false)
  private UUID requesterId;

  @Column(name = "addressee_id", nullable = false)
  private UUID addresseeId;

  @Column(nullable = false, length = 20)
  private String status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
```

- [ ] **Step 2: Create FriendshipRepository**

```java
package com.hackathon.features.friendships;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
  Optional<Friendship> findByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);
  List<Friendship> findByAddresseeIdAndStatus(UUID addresseeId, String status);
  List<Friendship> findByRequesterIdAndStatus(UUID requesterId, String status);

  @Query("SELECT f FROM Friendship f WHERE (f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'accepted'")
  List<Friendship> findAcceptedForUser(UUID userId);

  @Query("SELECT f FROM Friendship f WHERE ((f.requesterId = :a AND f.addresseeId = :b) OR (f.requesterId = :b AND f.addresseeId = :a))")
  Optional<Friendship> findBetween(UUID a, UUID b);
}
```

- [ ] **Step 3: Verify compile**

```bash
cd /src/ai_hakaton/backend && ./gradlew compileJava
```

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/friendships/
git commit -m "feat(friendships): add Friendship entity and repository"
```

---

### Task 8: FriendshipService TDD

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/friendships/FriendshipServiceTest.java`
- Create: `backend/src/main/java/com/hackathon/features/friendships/FriendshipService.java`
- Create: `backend/src/main/java/com/hackathon/features/bans/UserBan.java` (stub for test compile; full impl Task 10)
- Create: `backend/src/main/java/com/hackathon/features/bans/UserBanRepository.java` (stub)
- Create: `backend/src/main/java/com/hackathon/shared/websocket/FriendshipEventPublisher.java` (stub; full impl Task 15)

- [ ] **Step 1: Create UserBan + UserBanRepository stubs**

`backend/src/main/java/com/hackathon/features/bans/UserBan.java`:

```java
package com.hackathon.features.bans;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_bans",
    uniqueConstraints = @UniqueConstraint(columnNames = {"banner_id", "banned_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBan {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "banner_id", nullable = false)
  private UUID bannerId;

  @Column(name = "banned_id", nullable = false)
  private UUID bannedId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
```

`backend/src/main/java/com/hackathon/features/bans/UserBanRepository.java`:

```java
package com.hackathon.features.bans;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserBanRepository extends JpaRepository<UserBan, UUID> {
  boolean existsByBannerIdAndBannedId(UUID bannerId, UUID bannedId);
  List<UserBan> findByBannerId(UUID bannerId);
  void deleteByBannerIdAndBannedId(UUID bannerId, UUID bannedId);
}
```

- [ ] **Step 2: Create FriendshipEventPublisher stub**

`backend/src/main/java/com/hackathon/shared/websocket/FriendshipEventPublisher.java`:

```java
package com.hackathon.shared.websocket;

import com.hackathon.features.friendships.Friendship;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FriendshipEventPublisher {
  public void publishRequestCreated(UUID addresseeUserId, Friendship request) {}
  public void publishAccepted(UUID forUserId, UUID counterpartUserId, Friendship friendship) {}
  public void publishRemoved(UUID forUserId, UUID counterpartUserId) {}
}
```

- [ ] **Step 3: Write FriendshipServiceTest**

Create `backend/src/test/java/com/hackathon/features/friendships/FriendshipServiceTest.java`:

```java
package com.hackathon.features.friendships;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class FriendshipServiceTest {
  @Mock FriendshipRepository friendshipRepository;
  @Mock UserBanRepository userBanRepository;
  @Mock UserService userService;
  @Mock FriendshipEventPublisher eventPublisher;

  FriendshipService service;
  UUID meId;
  UUID otherId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new FriendshipService(friendshipRepository, userBanRepository, userService, eventPublisher);
    meId = UUID.randomUUID();
    otherId = UUID.randomUUID();
  }

  @Test
  void sendRequest_createsPendingWhenNoExistingRelationship() {
    when(userService.getUserByUsername("bob")).thenReturn(User.builder().id(otherId).username("bob").build());
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.empty());
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);
    when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0, Friendship.class));

    Friendship result = service.sendRequest(meId, "bob");

    assertEquals(Friendship.STATUS_PENDING, result.getStatus());
    assertEquals(meId, result.getRequesterId());
    assertEquals(otherId, result.getAddresseeId());
    verify(eventPublisher).publishRequestCreated(otherId, result);
  }

  @Test
  void sendRequest_autoAcceptsInversePending() {
    Friendship existing = Friendship.builder().id(UUID.randomUUID()).requesterId(otherId).addresseeId(meId).status(Friendship.STATUS_PENDING).build();
    when(userService.getUserByUsername("bob")).thenReturn(User.builder().id(otherId).username("bob").build());
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(existing));
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);
    when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0, Friendship.class));

    Friendship result = service.sendRequest(meId, "bob");

    assertEquals(Friendship.STATUS_ACCEPTED, result.getStatus());
    verify(eventPublisher).publishAccepted(otherId, meId, result);
    verify(eventPublisher).publishAccepted(meId, otherId, result);
  }

  @Test
  void sendRequest_rejectsSelf() {
    when(userService.getUserByUsername("me")).thenReturn(User.builder().id(meId).username("me").build());
    assertThrows(IllegalArgumentException.class, () -> service.sendRequest(meId, "me"));
    verify(friendshipRepository, never()).save(any());
  }

  @Test
  void sendRequest_rejectsIfAlreadyFriends() {
    Friendship existing = Friendship.builder().requesterId(otherId).addresseeId(meId).status(Friendship.STATUS_ACCEPTED).build();
    when(userService.getUserByUsername("bob")).thenReturn(User.builder().id(otherId).build());
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(existing));
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.sendRequest(meId, "bob"));
  }

  @Test
  void sendRequest_rejectsIfBanExistsEitherDirection() {
    when(userService.getUserByUsername("bob")).thenReturn(User.builder().id(otherId).build());
    when(userBanRepository.existsByBannerIdAndBannedId(eq(meId), eq(otherId))).thenReturn(false);
    when(userBanRepository.existsByBannerIdAndBannedId(eq(otherId), eq(meId))).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.sendRequest(meId, "bob"));
  }

  @Test
  void accept_onlyAddresseeCanAccept() {
    UUID requestId = create(UUID.class);
    Friendship pending = Friendship.builder().id(requestId).requesterId(otherId).addresseeId(meId).status(Friendship.STATUS_PENDING).build();
    when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pending));
    when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0, Friendship.class));

    Friendship result = service.accept(meId, requestId);

    assertEquals(Friendship.STATUS_ACCEPTED, result.getStatus());
    verify(eventPublisher).publishAccepted(otherId, meId, result);
  }

  @Test
  void accept_rejectsWhenRequesterTriesToAccept() {
    UUID requestId = create(UUID.class);
    Friendship pending = Friendship.builder().id(requestId).requesterId(meId).addresseeId(otherId).status(Friendship.STATUS_PENDING).build();
    when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pending));

    assertThrows(IllegalArgumentException.class, () -> service.accept(meId, requestId));
  }

  @Test
  void reject_deletesPendingRow() {
    UUID requestId = create(UUID.class);
    Friendship pending = Friendship.builder().id(requestId).requesterId(otherId).addresseeId(meId).status(Friendship.STATUS_PENDING).build();
    when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pending));

    service.reject(meId, requestId);

    verify(friendshipRepository).delete(pending);
  }

  @Test
  void listAcceptedFriends_returnsBothDirections() {
    when(friendshipRepository.findAcceptedForUser(meId)).thenReturn(List.of(
        Friendship.builder().requesterId(meId).addresseeId(otherId).status(Friendship.STATUS_ACCEPTED).build(),
        Friendship.builder().requesterId(UUID.randomUUID()).addresseeId(meId).status(Friendship.STATUS_ACCEPTED).build()));

    List<Friendship> friends = service.listAccepted(meId);

    assertEquals(2, friends.size());
  }

  @Test
  void removeFriend_deletesAcceptedRowAndPublishesEvent() {
    Friendship accepted = Friendship.builder().id(create(UUID.class)).requesterId(meId).addresseeId(otherId).status(Friendship.STATUS_ACCEPTED).build();
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(accepted));

    service.removeFriend(meId, otherId);

    verify(friendshipRepository).delete(accepted);
    verify(eventPublisher).publishRemoved(otherId, meId);
  }
}
```

- [ ] **Step 4: Verify test fails (FriendshipService doesn't exist)**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'FriendshipServiceTest' 2>&1 | tail -10
```

Expected: compile error on FriendshipService.

- [ ] **Step 5: Implement FriendshipService**

Create `backend/src/main/java/com/hackathon/features/friendships/FriendshipService.java`:

```java
package com.hackathon.features.friendships;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendshipService {
  private final FriendshipRepository friendshipRepository;
  private final UserBanRepository userBanRepository;
  private final UserService userService;
  private final FriendshipEventPublisher eventPublisher;

  @Transactional
  public Friendship sendRequest(UUID requesterId, String targetUsername) {
    User target = userService.getUserByUsername(targetUsername);
    UUID targetId = target.getId();
    if (targetId.equals(requesterId)) {
      throw new IllegalArgumentException("Cannot send friend request to yourself");
    }
    if (userBanRepository.existsByBannerIdAndBannedId(requesterId, targetId)
        || userBanRepository.existsByBannerIdAndBannedId(targetId, requesterId)) {
      throw new IllegalArgumentException("Cannot send friend request");
    }
    Optional<Friendship> existing = friendshipRepository.findBetween(requesterId, targetId);
    if (existing.isPresent()) {
      Friendship f = existing.get();
      if (Friendship.STATUS_ACCEPTED.equals(f.getStatus())) {
        throw new IllegalArgumentException("Already friends");
      }
      if (f.getRequesterId().equals(targetId) && f.getAddresseeId().equals(requesterId)) {
        f.setStatus(Friendship.STATUS_ACCEPTED);
        Friendship saved = friendshipRepository.save(f);
        eventPublisher.publishAccepted(targetId, requesterId, saved);
        eventPublisher.publishAccepted(requesterId, targetId, saved);
        return saved;
      }
      throw new IllegalArgumentException("Friend request already pending");
    }
    Friendship request = Friendship.builder()
        .requesterId(requesterId).addresseeId(targetId).status(Friendship.STATUS_PENDING).build();
    Friendship saved = friendshipRepository.save(request);
    eventPublisher.publishRequestCreated(targetId, saved);
    return saved;
  }

  @Transactional
  public Friendship accept(UUID currentUserId, UUID requestId) {
    Friendship request = friendshipRepository.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!request.getAddresseeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the addressee can accept");
    }
    if (!Friendship.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalArgumentException("Request is not pending");
    }
    request.setStatus(Friendship.STATUS_ACCEPTED);
    Friendship saved = friendshipRepository.save(request);
    eventPublisher.publishAccepted(request.getRequesterId(), currentUserId, saved);
    return saved;
  }

  @Transactional
  public void reject(UUID currentUserId, UUID requestId) {
    Friendship request = friendshipRepository.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!request.getAddresseeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the addressee can reject");
    }
    if (!Friendship.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalArgumentException("Request is not pending");
    }
    friendshipRepository.delete(request);
  }

  @Transactional
  public void cancel(UUID currentUserId, UUID requestId) {
    Friendship request = friendshipRepository.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!request.getRequesterId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the requester can cancel");
    }
    if (!Friendship.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalArgumentException("Request is not pending");
    }
    friendshipRepository.delete(request);
  }

  public List<Friendship> listAccepted(UUID userId) {
    return friendshipRepository.findAcceptedForUser(userId);
  }

  public List<Friendship> listPendingIncoming(UUID userId) {
    return friendshipRepository.findByAddresseeIdAndStatus(userId, Friendship.STATUS_PENDING);
  }

  public List<Friendship> listPendingOutgoing(UUID userId) {
    return friendshipRepository.findByRequesterIdAndStatus(userId, Friendship.STATUS_PENDING);
  }

  @Transactional
  public void removeFriend(UUID currentUserId, UUID otherUserId) {
    Friendship friendship = friendshipRepository.findBetween(currentUserId, otherUserId)
        .filter(f -> Friendship.STATUS_ACCEPTED.equals(f.getStatus()))
        .orElseThrow(() -> new IllegalArgumentException("Not friends"));
    friendshipRepository.delete(friendship);
    eventPublisher.publishRemoved(otherUserId, currentUserId);
  }
}
```

- [ ] **Step 6: Run test — verify PASS**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'FriendshipServiceTest'
```

Expected: 10 tests PASS.

- [ ] **Step 7: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/friendships/FriendshipService.java \
        backend/src/main/java/com/hackathon/features/bans/UserBan.java \
        backend/src/main/java/com/hackathon/features/bans/UserBanRepository.java \
        backend/src/main/java/com/hackathon/shared/websocket/FriendshipEventPublisher.java \
        backend/src/test/java/com/hackathon/features/friendships/FriendshipServiceTest.java
git commit -m "feat(friendships): FriendshipService with 10 TDD tests

- sendRequest: self-check, ban-check (either direction), auto-accept on inverse pending
- accept/reject: only addressee; request must be pending
- cancel: only requester
- removeFriend: delete accepted + publish event
- listAccepted/listPendingIncoming/listPendingOutgoing
- Event publisher calls stubbed (wired in Task 15)
- Adds UserBan entity+repo stubs (full UserBanService in Task 10)"
```

---

### Task 9: FriendshipController + tests

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/friendships/FriendshipControllerTest.java`
- Create: `backend/src/main/java/com/hackathon/features/friendships/FriendshipController.java`

- [ ] **Step 1: Write FriendshipControllerTest**

```java
package com.hackathon.features.friendships;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.TestSecurityConfig;
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
class FriendshipControllerTest {
  @Autowired MockMvc mvc;
  @MockBean FriendshipService friendshipService;
  @MockBean UserService userService;

  UUID meId;

  @BeforeEach
  void setUp() {
    meId = UUID.randomUUID();
    when(userService.getUserByUsername("user"))
        .thenReturn(User.builder().id(meId).username("user").build());
  }

  @Test @WithMockUser(username = "user")
  void sendRequest() throws Exception {
    Friendship f = Friendship.builder().id(UUID.randomUUID()).requesterId(meId)
        .addresseeId(UUID.randomUUID()).status(Friendship.STATUS_PENDING).build();
    when(friendshipService.sendRequest(eq(meId), eq("bob"))).thenReturn(f);

    mvc.perform(post("/api/friendships/requests").with(csrf())
            .contentType("application/json").content("{\"username\":\"bob\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"));
  }

  @Test @WithMockUser(username = "user")
  void listIncoming() throws Exception {
    when(friendshipService.listPendingIncoming(meId)).thenReturn(List.of(
        Friendship.builder().id(UUID.randomUUID()).requesterId(UUID.randomUUID())
            .addresseeId(meId).status(Friendship.STATUS_PENDING).build()));

    mvc.perform(get("/api/friendships/requests?direction=incoming"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("pending"));
  }

  @Test @WithMockUser(username = "user")
  void accept() throws Exception {
    UUID reqId = UUID.randomUUID();
    when(friendshipService.accept(meId, reqId))
        .thenReturn(Friendship.builder().id(reqId).status(Friendship.STATUS_ACCEPTED).build());

    mvc.perform(post("/api/friendships/requests/{id}/accept", reqId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));
  }

  @Test @WithMockUser(username = "user")
  void reject() throws Exception {
    UUID reqId = UUID.randomUUID();
    mvc.perform(post("/api/friendships/requests/{id}/reject", reqId).with(csrf()))
        .andExpect(status().isOk());
    verify(friendshipService).reject(meId, reqId);
  }

  @Test @WithMockUser(username = "user")
  void listFriends() throws Exception {
    UUID otherId = UUID.randomUUID();
    when(friendshipService.listAccepted(meId)).thenReturn(List.of(
        Friendship.builder().id(UUID.randomUUID()).requesterId(meId).addresseeId(otherId)
            .status(Friendship.STATUS_ACCEPTED).build()));
    when(userService.getUserById(otherId))
        .thenReturn(User.builder().id(otherId).username("bob").build());

    mvc.perform(get("/api/friendships"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(otherId.toString()))
        .andExpect(jsonPath("$[0].username").value("bob"));
  }

  @Test @WithMockUser(username = "user")
  void removeFriend() throws Exception {
    UUID otherId = UUID.randomUUID();
    mvc.perform(delete("/api/friendships/{id}", otherId).with(csrf()))
        .andExpect(status().isOk());
    verify(friendshipService).removeFriend(meId, otherId);
  }
}
```

- [ ] **Step 2: Verify test fails**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'FriendshipControllerTest' 2>&1 | tail -8
```

Expected: "cannot find symbol: class FriendshipController".

- [ ] **Step 3: Implement FriendshipController**

```java
package com.hackathon.features.friendships;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {
  private final FriendshipService friendshipService;
  private final UserService userService;

  record SendRequestBody(String username) {}
  record FriendView(UUID friendshipId, UUID userId, String username) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping("/requests")
  public ResponseEntity<Friendship> sendRequest(
      @RequestBody SendRequestBody body, Authentication authentication) {
    return ResponseEntity.ok(friendshipService.sendRequest(currentUserId(authentication), body.username()));
  }

  @GetMapping("/requests")
  public ResponseEntity<List<Friendship>> listRequests(
      @RequestParam(defaultValue = "incoming") String direction, Authentication authentication) {
    UUID me = currentUserId(authentication);
    return ResponseEntity.ok("outgoing".equals(direction)
        ? friendshipService.listPendingOutgoing(me)
        : friendshipService.listPendingIncoming(me));
  }

  @PostMapping("/requests/{id}/accept")
  public ResponseEntity<Friendship> accept(@PathVariable UUID id, Authentication authentication) {
    return ResponseEntity.ok(friendshipService.accept(currentUserId(authentication), id));
  }

  @PostMapping("/requests/{id}/reject")
  public ResponseEntity<Void> reject(@PathVariable UUID id, Authentication authentication) {
    friendshipService.reject(currentUserId(authentication), id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/requests/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable UUID id, Authentication authentication) {
    friendshipService.cancel(currentUserId(authentication), id);
    return ResponseEntity.ok().build();
  }

  @GetMapping
  public ResponseEntity<List<FriendView>> listFriends(Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<FriendView> views = friendshipService.listAccepted(me).stream().map(f -> {
      UUID otherId = f.getRequesterId().equals(me) ? f.getAddresseeId() : f.getRequesterId();
      User other = userService.getUserById(otherId);
      return new FriendView(f.getId(), otherId, other.getUsername());
    }).toList();
    return ResponseEntity.ok(views);
  }

  @DeleteMapping("/{friendUserId}")
  public ResponseEntity<Void> removeFriend(
      @PathVariable UUID friendUserId, Authentication authentication) {
    friendshipService.removeFriend(currentUserId(authentication), friendUserId);
    return ResponseEntity.ok().build();
  }
}
```

- [ ] **Step 4: Run tests — verify PASS**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'FriendshipControllerTest'
```

Expected: 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/friendships/FriendshipController.java \
        backend/src/test/java/com/hackathon/features/friendships/FriendshipControllerTest.java
git commit -m "feat(friendships): REST controller with 6 endpoints"
```

---

## Section C: User Bans

### Task 10: UserBan package (service + controller + tests)

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/bans/UserBanService.java`
- Create: `backend/src/main/java/com/hackathon/features/bans/UserBanController.java`
- Create: `backend/src/test/java/com/hackathon/features/bans/UserBanServiceTest.java`
- Create: `backend/src/test/java/com/hackathon/features/bans/UserBanControllerTest.java`
- Modify: `backend/src/main/java/com/hackathon/features/friendships/FriendshipRepository.java` — add delete helper

> `UserBan` entity and `UserBanRepository` already exist as stubs from Task 8. This task implements the service/controller and wires real deletion of friendships on ban.

- [ ] **Step 1: Add delete helper to FriendshipRepository**

In `backend/src/main/java/com/hackathon/features/friendships/FriendshipRepository.java`, add method:

```java
  void deleteByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);
```

(Inside the same interface, after the existing query methods.)

- [ ] **Step 2: Write UserBanServiceTest**

Create `backend/src/test/java/com/hackathon/features/bans/UserBanServiceTest.java`:

```java
package com.hackathon.features.bans;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserBanServiceTest {
  @Mock UserBanRepository userBanRepository;
  @Mock FriendshipRepository friendshipRepository;
  @Mock FriendshipEventPublisher eventPublisher;

  UserBanService service;
  UUID meId;
  UUID otherId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new UserBanService(userBanRepository, friendshipRepository, eventPublisher);
    meId = UUID.randomUUID();
    otherId = UUID.randomUUID();
  }

  @Test
  void ban_insertsBanAndDeletesExistingFriendship() {
    Friendship friendship = Friendship.builder().requesterId(meId).addresseeId(otherId)
        .status(Friendship.STATUS_ACCEPTED).build();
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(friendship));
    when(userBanRepository.save(any(UserBan.class))).thenAnswer(inv -> inv.getArgument(0, UserBan.class));

    service.ban(meId, otherId);

    verify(userBanRepository).save(any(UserBan.class));
    verify(friendshipRepository).delete(friendship);
    verify(eventPublisher).publishRemoved(otherId, meId);
  }

  @Test
  void ban_worksWhenNoFriendshipExists() {
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.empty());
    when(userBanRepository.save(any(UserBan.class))).thenAnswer(inv -> inv.getArgument(0, UserBan.class));

    service.ban(meId, otherId);

    verify(userBanRepository).save(any(UserBan.class));
    verify(friendshipRepository, never()).delete(any());
  }

  @Test
  void ban_rejectsSelf() {
    assertThrows(IllegalArgumentException.class, () -> service.ban(meId, meId));
    verifyNoInteractions(userBanRepository);
  }

  @Test
  void listBans_returnsBansBelongingToUser() {
    when(userBanRepository.findByBannerId(meId)).thenReturn(List.of(
        UserBan.builder().bannerId(meId).bannedId(otherId).build()));

    List<UserBan> result = service.listBans(meId);

    assertEquals(1, result.size());
  }
}
```

- [ ] **Step 3: Verify test fails**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'UserBanServiceTest' 2>&1 | tail -8
```

Expected: "cannot find symbol: class UserBanService".

- [ ] **Step 4: Implement UserBanService**

Create `backend/src/main/java/com/hackathon/features/bans/UserBanService.java`:

```java
package com.hackathon.features.bans;

import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBanService {
  private final UserBanRepository userBanRepository;
  private final FriendshipRepository friendshipRepository;
  private final FriendshipEventPublisher eventPublisher;

  @Transactional
  public UserBan ban(UUID bannerId, UUID bannedId) {
    if (bannerId.equals(bannedId)) {
      throw new IllegalArgumentException("Cannot ban yourself");
    }
    UserBan ban = UserBan.builder().bannerId(bannerId).bannedId(bannedId).build();
    UserBan saved = userBanRepository.save(ban);

    Optional<Friendship> friendship = friendshipRepository.findBetween(bannerId, bannedId);
    if (friendship.isPresent()) {
      friendshipRepository.delete(friendship.get());
      eventPublisher.publishRemoved(bannedId, bannerId);
    }
    return saved;
  }

  public List<UserBan> listBans(UUID bannerId) {
    return userBanRepository.findByBannerId(bannerId);
  }
}
```

- [ ] **Step 5: Run service test — PASS**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'UserBanServiceTest'
```

Expected: 4 tests PASS.

- [ ] **Step 6: Write UserBanControllerTest**

```java
package com.hackathon.features.bans;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.TestSecurityConfig;
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
class UserBanControllerTest {
  @Autowired MockMvc mvc;
  @MockBean UserBanService userBanService;
  @MockBean UserService userService;

  UUID meId;

  @BeforeEach
  void setUp() {
    meId = UUID.randomUUID();
    when(userService.getUserByUsername("user"))
        .thenReturn(User.builder().id(meId).username("user").build());
  }

  @Test @WithMockUser(username = "user")
  void banUser() throws Exception {
    UUID bannedId = UUID.randomUUID();
    mvc.perform(post("/api/bans").with(csrf())
            .contentType("application/json")
            .content("{\"userId\":\"" + bannedId + "\"}"))
        .andExpect(status().isOk());
    verify(userBanService).ban(eq(meId), eq(bannedId));
  }

  @Test @WithMockUser(username = "user")
  void listBans() throws Exception {
    when(userBanService.listBans(meId)).thenReturn(java.util.List.of());
    mvc.perform(get("/api/bans")).andExpect(status().isOk());
  }
}
```

- [ ] **Step 7: Implement UserBanController**

```java
package com.hackathon.features.bans;

import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bans")
@RequiredArgsConstructor
public class UserBanController {
  private final UserBanService userBanService;
  private final UserService userService;

  record BanRequest(UUID userId) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping
  public ResponseEntity<UserBan> ban(@RequestBody BanRequest body, Authentication authentication) {
    return ResponseEntity.ok(userBanService.ban(currentUserId(authentication), body.userId()));
  }

  @GetMapping
  public ResponseEntity<List<UserBan>> list(Authentication authentication) {
    return ResponseEntity.ok(userBanService.listBans(currentUserId(authentication)));
  }
}
```

- [ ] **Step 8: Run controller test — PASS**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'UserBanControllerTest'
```

Expected: 2 tests PASS.

- [ ] **Step 9: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/bans/UserBanService.java \
        backend/src/main/java/com/hackathon/features/bans/UserBanController.java \
        backend/src/main/java/com/hackathon/features/friendships/FriendshipRepository.java \
        backend/src/test/java/com/hackathon/features/bans/UserBanServiceTest.java \
        backend/src/test/java/com/hackathon/features/bans/UserBanControllerTest.java
git commit -m "feat(bans): UserBanService + controller with tests

- ban: self-check, insert ban row, cascade-delete friendship (publish removed event)
- listBans: return bans where current user is banner
- Controller: POST /api/bans, GET /api/bans"
```

---

## Section D: Direct Messages

### Task 11: DM entities + repositories

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectConversation.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectConversationRepository.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectMessage.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectMessageRepository.java`

- [ ] **Step 1: Create DirectConversation**

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

@Entity
@Table(name = "direct_conversations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user1_id", "user2_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DirectConversation {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user1_id", nullable = false)
  private UUID user1Id;

  @Column(name = "user2_id", nullable = false)
  private UUID user2Id;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
```

- [ ] **Step 2: Create DirectConversationRepository**

```java
package com.hackathon.features.dms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectConversationRepository extends JpaRepository<DirectConversation, UUID> {
  Optional<DirectConversation> findByUser1IdAndUser2Id(UUID user1Id, UUID user2Id);

  @Query("SELECT c FROM DirectConversation c WHERE c.user1Id = :userId OR c.user2Id = :userId ORDER BY c.createdAt DESC")
  List<DirectConversation> findAllForUser(UUID userId);
}
```

- [ ] **Step 3: Create DirectMessage**

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
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DirectMessage {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
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
}
```

- [ ] **Step 4: Create DirectMessageRepository**

```java
package com.hackathon.features.dms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
  @Query(value = "SELECT * FROM direct_messages WHERE conversation_id = ?1 ORDER BY created_at DESC", nativeQuery = true)
  List<DirectMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

  @Query(value = "SELECT * FROM direct_messages WHERE conversation_id = ?1 AND created_at < "
      + "(SELECT created_at FROM direct_messages WHERE id = ?2) ORDER BY created_at DESC",
      nativeQuery = true)
  List<DirectMessage> findByConversationIdBeforeCursor(UUID conversationId, UUID beforeMessageId, Pageable pageable);

  Optional<DirectMessage> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
```

- [ ] **Step 5: Compile + commit**

```bash
cd /src/ai_hakaton/backend && ./gradlew compileJava
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/dms/
git commit -m "feat(dms): add DirectConversation and DirectMessage entities + repositories

- DirectConversationRepository: get-by-pair, list-for-user
- DirectMessageRepository: cursor-paginated history, findTop for last-message preview"
```

---

### Task 12: ConversationService with tests

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/dms/ConversationServiceTest.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/ConversationService.java`

- [ ] **Step 1: Write ConversationServiceTest**

```java
package com.hackathon.features.dms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ConversationServiceTest {
  @Mock DirectConversationRepository directConversationRepository;
  ConversationService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ConversationService(directConversationRepository);
  }

  @Test
  void getOrCreate_returnsExistingWhenPresent_regardlessOfArgOrder() {
    UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
    DirectConversation existing = DirectConversation.builder().id(UUID.randomUUID())
        .user1Id(a).user2Id(b).build();
    when(directConversationRepository.findByUser1IdAndUser2Id(a, b)).thenReturn(Optional.of(existing));

    DirectConversation r1 = service.getOrCreate(a, b);
    DirectConversation r2 = service.getOrCreate(b, a); // inverse order

    assertSame(existing, r1);
    assertSame(existing, r2);
    verify(directConversationRepository, never()).save(any());
  }

  @Test
  void getOrCreate_createsWithCanonicalOrdering_whenCalledWithHigherFirst() {
    UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
    when(directConversationRepository.findByUser1IdAndUser2Id(a, b)).thenReturn(Optional.empty());
    when(directConversationRepository.save(any(DirectConversation.class)))
        .thenAnswer(inv -> inv.getArgument(0, DirectConversation.class));

    DirectConversation created = service.getOrCreate(b, a);

    assertEquals(a, created.getUser1Id()); // canonical: lower UUID first
    assertEquals(b, created.getUser2Id());
  }

  @Test
  void getOrCreate_rejectsSameUser() {
    UUID a = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> service.getOrCreate(a, a));
  }
}
```

- [ ] **Step 2: Verify test fails (no ConversationService yet)**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'ConversationServiceTest' 2>&1 | tail -5
```

- [ ] **Step 3: Implement ConversationService**

```java
package com.hackathon.features.dms;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {
  private final DirectConversationRepository directConversationRepository;

  @Transactional
  public DirectConversation getOrCreate(UUID userA, UUID userB) {
    if (userA.equals(userB)) {
      throw new IllegalArgumentException("Cannot have a conversation with yourself");
    }
    UUID user1 = userA.compareTo(userB) < 0 ? userA : userB;
    UUID user2 = userA.compareTo(userB) < 0 ? userB : userA;
    return directConversationRepository.findByUser1IdAndUser2Id(user1, user2)
        .orElseGet(() -> directConversationRepository.save(
            DirectConversation.builder().user1Id(user1).user2Id(user2).build()));
  }

  public UUID otherParticipant(DirectConversation conversation, UUID currentUserId) {
    return conversation.getUser1Id().equals(currentUserId)
        ? conversation.getUser2Id() : conversation.getUser1Id();
  }
}
```

- [ ] **Step 4: Run tests — PASS**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'ConversationServiceTest'
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/dms/ConversationService.java \
        backend/src/test/java/com/hackathon/features/dms/ConversationServiceTest.java
git commit -m "feat(dms): ConversationService with canonical user ordering

- getOrCreate enforces user1Id < user2Id regardless of argument order
- Returns existing conversation if present
- Rejects same-user"
```

---

### Task 13: DirectMessageService with tests

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/dms/DirectMessageServiceTest.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java`

- [ ] **Step 1: Write DirectMessageServiceTest**

```java
package com.hackathon.features.dms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

class DirectMessageServiceTest {
  @Mock DirectMessageRepository directMessageRepository;
  @Mock DirectConversationRepository directConversationRepository;
  @Mock ConversationService conversationService;
  @Mock FriendshipRepository friendshipRepository;
  @Mock UserBanRepository userBanRepository;

  DirectMessageService service;
  UUID meId;
  UUID otherId;
  UUID conversationId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new DirectMessageService(directMessageRepository, directConversationRepository,
        conversationService, friendshipRepository, userBanRepository);
    meId = UUID.randomUUID();
    otherId = UUID.randomUUID();
    conversationId = UUID.randomUUID();
  }

  @Test
  void send_persistsWhenFriendsAndNotBanned() {
    DirectConversation conv = DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(
        Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);
    when(directMessageRepository.save(any(DirectMessage.class)))
        .thenAnswer(inv -> inv.getArgument(0, DirectMessage.class));

    DirectMessage saved = service.send(meId, conversationId, "hello");

    assertEquals("hello", saved.getText());
    assertEquals(meId, saved.getSenderId());
    assertEquals(conversationId, saved.getConversationId());
  }

  @Test
  void send_rejectsNonParticipant() {
    DirectConversation conv = DirectConversation.builder().id(conversationId)
        .user1Id(UUID.randomUUID()).user2Id(UUID.randomUUID()).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "hi"));
  }

  @Test
  void send_rejectsWhenNotFriends() {
    DirectConversation conv = DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "hi"));
  }

  @Test
  void send_rejectsWhenBanEitherDirection() {
    DirectConversation conv = DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(
        Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));
    when(userBanRepository.existsByBannerIdAndBannedId(meId, otherId)).thenReturn(false);
    when(userBanRepository.existsByBannerIdAndBannedId(otherId, meId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "hi"));
  }

  @Test
  void send_rejectsEmptyText() {
    DirectConversation conv = DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(
        Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "  "));
  }

  @Test
  void send_rejectsTextOver3072() {
    DirectConversation conv = DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(
        Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "x".repeat(3073)));
  }

  @Test
  void getHistory_withoutCursor_usesOrderByCreatedAt() {
    when(directMessageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 50)))
        .thenReturn(List.of(DirectMessage.builder().text("a").build()));

    List<DirectMessage> result = service.getHistory(conversationId, null, 50);

    assertEquals(1, result.size());
  }

  @Test
  void getHistory_withCursor_usesBeforeCursor() {
    UUID beforeId = UUID.randomUUID();
    when(directMessageRepository.findByConversationIdBeforeCursor(conversationId, beforeId, PageRequest.of(0, 50)))
        .thenReturn(List.of());

    List<DirectMessage> result = service.getHistory(conversationId, beforeId, 50);

    assertEquals(0, result.size());
  }
}
```

- [ ] **Step 2: Verify test fails**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'DirectMessageServiceTest' 2>&1 | tail -5
```

- [ ] **Step 3: Implement DirectMessageService**

```java
package com.hackathon.features.dms;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

  @Transactional
  public DirectMessage send(UUID senderId, UUID conversationId, String text) {
    DirectConversation conv = directConversationRepository.findById(conversationId)
        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
    if (!conv.getUser1Id().equals(senderId) && !conv.getUser2Id().equals(senderId)) {
      throw new IllegalArgumentException("Not a participant of this conversation");
    }
    UUID other = conversationService.otherParticipant(conv, senderId);
    ensureFriendsAndNotBanned(senderId, other);
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Message text cannot be empty");
    }
    if (text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("Message exceeds 3072 characters");
    }
    return directMessageRepository.save(DirectMessage.builder()
        .conversationId(conversationId).senderId(senderId).text(text).build());
  }

  @Transactional
  public DirectMessage sendToUser(UUID senderId, UUID recipientId, String text) {
    ensureFriendsAndNotBanned(senderId, recipientId);
    DirectConversation conv = conversationService.getOrCreate(senderId, recipientId);
    return send(senderId, conv.getId(), text);
  }

  public List<DirectMessage> getHistory(UUID conversationId, UUID beforeMessageId, int limit) {
    return beforeMessageId == null
        ? directMessageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit))
        : directMessageRepository.findByConversationIdBeforeCursor(conversationId, beforeMessageId, PageRequest.of(0, limit));
  }

  public Optional<DirectMessage> lastMessage(UUID conversationId) {
    return directMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId);
  }

  public List<DirectConversation> listConversations(UUID userId) {
    return directConversationRepository.findAllForUser(userId);
  }

  private void ensureFriendsAndNotBanned(UUID me, UUID other) {
    Friendship friendship = friendshipRepository.findBetween(me, other)
        .filter(f -> Friendship.STATUS_ACCEPTED.equals(f.getStatus()))
        .orElseThrow(() -> new IllegalArgumentException("You must be friends to send a direct message"));
    if (userBanRepository.existsByBannerIdAndBannedId(me, other)
        || userBanRepository.existsByBannerIdAndBannedId(other, me)) {
      throw new IllegalArgumentException("Cannot send direct message");
    }
  }
}
```

- [ ] **Step 4: Run test — PASS**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'DirectMessageServiceTest'
```

Expected: 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java \
        backend/src/test/java/com/hackathon/features/dms/DirectMessageServiceTest.java
git commit -m "feat(dms): DirectMessageService with validation and cursor pagination

- send: validate participant, friendship, no-ban-either-direction, text length
- sendToUser: get-or-create conversation + send
- getHistory: cursor-paginated, 50 max
- lastMessage: for conversation previews"
```

---

### Task 14: DirectMessageController + tests

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/dms/DirectMessageControllerTest.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectMessageController.java`

- [ ] **Step 1: Write DirectMessageControllerTest**

```java
package com.hackathon.features.dms;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.TestSecurityConfig;
import java.util.List;
import java.util.Optional;
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
class DirectMessageControllerTest {
  @Autowired MockMvc mvc;
  @MockBean DirectMessageService directMessageService;
  @MockBean ConversationService conversationService;
  @MockBean UserService userService;

  UUID meId;

  @BeforeEach
  void setUp() {
    meId = UUID.randomUUID();
    when(userService.getUserByUsername("user"))
        .thenReturn(User.builder().id(meId).username("user").build());
  }

  @Test @WithMockUser(username = "user")
  void listConversations() throws Exception {
    UUID otherId = UUID.randomUUID();
    DirectConversation conv = DirectConversation.builder().id(UUID.randomUUID())
        .user1Id(meId).user2Id(otherId).build();
    when(directMessageService.listConversations(meId)).thenReturn(List.of(conv));
    when(directMessageService.lastMessage(conv.getId())).thenReturn(Optional.of(
        DirectMessage.builder().id(UUID.randomUUID()).text("hey").senderId(otherId).build()));
    when(userService.getUserById(otherId)).thenReturn(User.builder().id(otherId).username("bob").build());

    mvc.perform(get("/api/dms/conversations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].otherUsername").value("bob"))
        .andExpect(jsonPath("$[0].lastMessage").value("hey"));
  }

  @Test @WithMockUser(username = "user")
  void getOrCreateWithOther() throws Exception {
    UUID otherId = UUID.randomUUID();
    UUID convId = UUID.randomUUID();
    when(conversationService.getOrCreate(eq(meId), eq(otherId)))
        .thenReturn(DirectConversation.builder().id(convId).user1Id(meId).user2Id(otherId).build());

    mvc.perform(get("/api/dms/with/{otherId}", otherId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(convId.toString()));
  }

  @Test @WithMockUser(username = "user")
  void getHistory() throws Exception {
    UUID convId = UUID.randomUUID();
    when(directMessageService.getHistory(convId, null, 50)).thenReturn(List.of(
        DirectMessage.builder().id(UUID.randomUUID()).text("a").senderId(meId).build()));

    mvc.perform(get("/api/dms/{id}/messages", convId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("a"));
  }

  @Test @WithMockUser(username = "user")
  void sendViaRest() throws Exception {
    UUID convId = UUID.randomUUID();
    DirectMessage saved = DirectMessage.builder().id(UUID.randomUUID())
        .conversationId(convId).senderId(meId).text("hi").build();
    when(directMessageService.send(meId, convId, "hi")).thenReturn(saved);

    mvc.perform(post("/api/dms/{id}/messages", convId).with(csrf())
            .contentType("application/json").content("{\"text\":\"hi\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("hi"));
  }
}
```

- [ ] **Step 2: Verify test fails**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'DirectMessageControllerTest' 2>&1 | tail -5
```

- [ ] **Step 3: Implement DirectMessageController**

```java
package com.hackathon.features.dms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
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

  record SendMessageBody(String text) {}

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
    List<ConversationView> views = directMessageService.listConversations(me).stream().map(conv -> {
      UUID otherId = conversationService.otherParticipant(conv, me);
      User other = userService.getUserById(otherId);
      var last = directMessageService.lastMessage(conv.getId()).orElse(null);
      return new ConversationView(
          conv.getId(), otherId, other.getUsername(),
          last != null ? last.getText() : null,
          last != null ? last.getCreatedAt() : null);
    }).toList();
    return ResponseEntity.ok(views);
  }

  @GetMapping("/with/{otherUserId}")
  public ResponseEntity<DirectConversation> getOrCreate(
      @PathVariable UUID otherUserId, Authentication authentication) {
    return ResponseEntity.ok(conversationService.getOrCreate(currentUserId(authentication), otherUserId));
  }

  @GetMapping("/{conversationId}/messages")
  public ResponseEntity<List<DirectMessage>> getHistory(
      @PathVariable UUID conversationId,
      @RequestParam(required = false) UUID before,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(directMessageService.getHistory(conversationId, before, limit));
  }

  @PostMapping("/{conversationId}/messages")
  public ResponseEntity<DirectMessage> sendMessage(
      @PathVariable UUID conversationId,
      @RequestBody SendMessageBody body,
      Authentication authentication) {
    return ResponseEntity.ok(directMessageService.send(currentUserId(authentication), conversationId, body.text()));
  }
}
```

- [ ] **Step 4: Run test — PASS**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'DirectMessageControllerTest'
```

Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/dms/DirectMessageController.java \
        backend/src/test/java/com/hackathon/features/dms/DirectMessageControllerTest.java
git commit -m "feat(dms): REST controller for conversations and messages

- GET /api/dms/conversations (with ConversationView + last-message preview)
- GET /api/dms/with/{otherUserId} (get-or-create)
- GET /api/dms/{id}/messages (cursor pagination)
- POST /api/dms/{id}/messages (REST send)"
```

---

## Section E: Real-time WebSocket

### Task 15: DirectMessageHandler + FriendshipEventPublisher

**Files:**
- Create: `backend/src/main/java/com/hackathon/shared/websocket/DirectMessageHandler.java`
- Modify: `backend/src/main/java/com/hackathon/shared/websocket/FriendshipEventPublisher.java` (full impl)
- Modify: `backend/src/main/java/com/hackathon/shared/websocket/WebSocketConfig.java` (add user-destination prefix)

- [ ] **Step 1: Replace FriendshipEventPublisher with real implementation**

```java
package com.hackathon.shared.websocket;

import com.hackathon.features.friendships.Friendship;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendshipEventPublisher {
  private final SimpMessagingTemplate messagingTemplate;

  public void publishRequestCreated(UUID addresseeUserId, Friendship request) {
    messagingTemplate.convertAndSendToUser(addresseeUserId.toString(), "/queue/friend-events",
        Map.of("type", "REQUEST_CREATED", "friendship", request));
  }

  public void publishAccepted(UUID forUserId, UUID counterpartUserId, Friendship friendship) {
    messagingTemplate.convertAndSendToUser(forUserId.toString(), "/queue/friend-events",
        Map.of("type", "FRIENDSHIP_ACCEPTED", "counterpartUserId", counterpartUserId, "friendship", friendship));
  }

  public void publishRemoved(UUID forUserId, UUID counterpartUserId) {
    messagingTemplate.convertAndSendToUser(forUserId.toString(), "/queue/friend-events",
        Map.of("type", "FRIENDSHIP_REMOVED", "counterpartUserId", counterpartUserId));
  }
}
```

**Note:** `convertAndSendToUser(userId, "/queue/friend-events", ...)` sends to `/user/{userId}/queue/friend-events`. The client must subscribe to `/user/queue/friend-events` (Spring resolves `/user` to the current user's sessions). To make this work, our `UserPrincipal` must match the userId string — we configure that in Step 3.

- [ ] **Step 2: Create DirectMessageHandler**

```java
package com.hackathon.shared.websocket;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectConversationRepository;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.users.UserService;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageHandler {
  private final DirectMessageService directMessageService;
  private final DirectConversationRepository directConversationRepository;
  private final ConversationService conversationService;
  private final UserService userService;
  private final SimpMessagingTemplate messagingTemplate;

  record DmPayload(String text) {}

  public record DmEvent(
      UUID id, UUID conversationId, UUID senderId, String senderUsername, String text, OffsetDateTime createdAt) {}

  @MessageMapping("/dms/{conversationId}/message")
  public void handleDirectMessage(
      @DestinationVariable UUID conversationId, DmPayload payload, Principal principal) {
    var sender = userService.getUserByUsername(principal.getName());
    DirectMessage saved = directMessageService.send(sender.getId(), conversationId, payload.text());

    DirectConversation conv = directConversationRepository.findById(conversationId).orElseThrow();
    UUID otherId = conversationService.otherParticipant(conv, sender.getId());

    DmEvent event = new DmEvent(saved.getId(), saved.getConversationId(), saved.getSenderId(),
        sender.getUsername(), saved.getText(), saved.getCreatedAt());

    messagingTemplate.convertAndSendToUser(sender.getId().toString(), "/queue/dms", event);
    messagingTemplate.convertAndSendToUser(otherId.toString(), "/queue/dms", event);
  }
}
```

- [ ] **Step 3: Update WebSocketConfig to use userId as Principal name**

Add a `ChannelInterceptor` to the existing `WebSocketConfig.java` so the WebSocket `Principal.getName()` returns the authenticated user's UUID string. Replace `configureMessageBroker` and add `configureClientInboundChannel`:

```java
package com.hackathon.shared.websocket;

import com.hackathon.features.users.UserService;
import java.security.Principal;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  @Autowired private com.hackathon.shared.security.JwtTokenProvider jwtTokenProvider;
  @Autowired private UserService userService;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic", "/queue", "/user");
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*").withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
      @Override
      public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
          String authHeader = accessor.getFirstNativeHeader("Authorization");
          if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
              var userId = jwtTokenProvider.getUserIdFromToken(token);
              Principal principal = new UsernamePasswordAuthenticationToken(
                  userId.toString(), null, Collections.emptyList());
              accessor.setUser(principal);
            }
          }
        }
        return message;
      }
    });
  }
}
```

**Note:** After this change, `Principal.getName()` in WebSocket handlers returns the user's UUID string. `ChatMessageHandler` was written against `principal.getName()` returning a username — we need to update it.

- [ ] **Step 4: Update ChatMessageHandler to resolve user from UUID principal**

In `backend/src/main/java/com/hackathon/shared/websocket/ChatMessageHandler.java`, change the body of `handleMessage` to use UUID:

```java
@MessageMapping("/rooms/{roomId}/message")
@SendTo("/topic/room/{roomId}")
public ChatMessageDTO handleMessage(
    ChatMessageDTO payload, @DestinationVariable UUID roomId, Principal principal) {
  UUID userId = UUID.fromString(principal.getName());
  var user = userService.getUserById(userId);
  Message saved = messageService.sendMessage(roomId, userId, payload.getText());
  return ChatMessageDTO.builder()
      .id(saved.getId())
      .roomId(saved.getRoomId())
      .userId(saved.getUserId())
      .username(user.getUsername())
      .text(saved.getText())
      .createdAt(saved.getCreatedAt())
      .build();
}
```

In `DirectMessageHandler` do the same:

```java
UUID senderUserId = UUID.fromString(principal.getName());
var sender = userService.getUserById(senderUserId);
```

Adjust the method accordingly.

- [ ] **Step 5: Compile and run all tests**

```bash
cd /src/ai_hakaton/backend && ./gradlew test
```

Expected: all tests PASS. Existing ChatMessageHandler tests (if any) may need updates — if you see failures there, update the test's `Principal` mock to return a UUID string.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/shared/websocket/
git commit -m "feat(websocket): DM handler + FriendshipEventPublisher + userId as Principal

- ChannelInterceptor parses Authorization header on STOMP CONNECT, sets userId as Principal name
- DirectMessageHandler: @MessageMapping for /app/dms/{id}/message, pushes to /user/{uuid}/queue/dms for both participants
- FriendshipEventPublisher: fires REQUEST_CREATED / FRIENDSHIP_ACCEPTED / FRIENDSHIP_REMOVED events to /user/{uuid}/queue/friend-events
- ChatMessageHandler updated to read UUID from Principal"
```

---

## Section F: Frontend

### Task 16: Types + services

**Files:**
- Create: `frontend/src/types/friendship.ts`
- Create: `frontend/src/types/ban.ts`
- Create: `frontend/src/types/directMessage.ts`
- Create: `frontend/src/services/friendshipService.ts`
- Create: `frontend/src/services/banService.ts`
- Create: `frontend/src/services/directMessageService.ts`

- [ ] **Step 1: Types**

`frontend/src/types/friendship.ts`:

```typescript
export type FriendshipStatus = 'pending' | 'accepted';

export interface Friendship {
  id: string;
  requesterId: string;
  addresseeId: string;
  status: FriendshipStatus;
  createdAt: string;
  updatedAt: string;
}

export interface FriendView {
  friendshipId: string;
  userId: string;
  username: string;
}
```

`frontend/src/types/ban.ts`:

```typescript
export interface UserBan {
  id: string;
  bannerId: string;
  bannedId: string;
  createdAt: string;
}
```

`frontend/src/types/directMessage.ts`:

```typescript
export interface DirectConversation {
  id: string;
  user1Id: string;
  user2Id: string;
  createdAt: string;
}

export interface ConversationView {
  id: string;
  otherUserId: string;
  otherUsername: string;
  lastMessage: string | null;
  lastMessageAt: string | null;
}

export interface DirectMessage {
  id: string;
  conversationId: string;
  senderId: string;
  text: string;
  createdAt: string;
}

export type FriendEvent =
  | { type: 'REQUEST_CREATED'; friendship: import('./friendship').Friendship }
  | { type: 'FRIENDSHIP_ACCEPTED'; counterpartUserId: string; friendship: import('./friendship').Friendship }
  | { type: 'FRIENDSHIP_REMOVED'; counterpartUserId: string };
```

- [ ] **Step 2: friendshipService**

`frontend/src/services/friendshipService.ts`:

```typescript
import axios from 'axios';
import { Friendship, FriendView } from '../types/friendship';

const BASE = '/api/friendships';

export const friendshipService = {
  async sendRequest(username: string): Promise<Friendship> {
    return (await axios.post(`${BASE}/requests`, { username })).data;
  },
  async listIncoming(): Promise<Friendship[]> {
    return (await axios.get(`${BASE}/requests?direction=incoming`)).data;
  },
  async listOutgoing(): Promise<Friendship[]> {
    return (await axios.get(`${BASE}/requests?direction=outgoing`)).data;
  },
  async accept(requestId: string): Promise<Friendship> {
    return (await axios.post(`${BASE}/requests/${requestId}/accept`)).data;
  },
  async reject(requestId: string): Promise<void> {
    await axios.post(`${BASE}/requests/${requestId}/reject`);
  },
  async cancel(requestId: string): Promise<void> {
    await axios.post(`${BASE}/requests/${requestId}/cancel`);
  },
  async listFriends(): Promise<FriendView[]> {
    return (await axios.get(BASE)).data;
  },
  async removeFriend(friendUserId: string): Promise<void> {
    await axios.delete(`${BASE}/${friendUserId}`);
  },
};
```

- [ ] **Step 3: banService**

`frontend/src/services/banService.ts`:

```typescript
import axios from 'axios';
import { UserBan } from '../types/ban';

export const banService = {
  async banUser(userId: string): Promise<UserBan> {
    return (await axios.post('/api/bans', { userId })).data;
  },
  async listBans(): Promise<UserBan[]> {
    return (await axios.get('/api/bans')).data;
  },
};
```

- [ ] **Step 4: directMessageService**

`frontend/src/services/directMessageService.ts`:

```typescript
import axios from 'axios';
import { ConversationView, DirectConversation, DirectMessage } from '../types/directMessage';

export const directMessageService = {
  async listConversations(): Promise<ConversationView[]> {
    return (await axios.get('/api/dms/conversations')).data;
  },
  async getOrCreateWith(otherUserId: string): Promise<DirectConversation> {
    return (await axios.get(`/api/dms/with/${otherUserId}`)).data;
  },
  async getHistory(conversationId: string, before?: string, limit = 50): Promise<DirectMessage[]> {
    const params = new URLSearchParams();
    if (before) params.append('before', before);
    params.append('limit', String(limit));
    return (await axios.get(`/api/dms/${conversationId}/messages?${params}`)).data;
  },
  async sendMessage(conversationId: string, text: string): Promise<DirectMessage> {
    return (await axios.post(`/api/dms/${conversationId}/messages`, { text })).data;
  },
};
```

- [ ] **Step 5: Verify build**

```bash
cd /src/ai_hakaton/frontend && npm run build
```

Expected: success.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/types/friendship.ts frontend/src/types/ban.ts frontend/src/types/directMessage.ts \
        frontend/src/services/friendshipService.ts frontend/src/services/banService.ts \
        frontend/src/services/directMessageService.ts
git commit -m "feat(frontend): friendship/ban/DM types and services"
```

---

### Task 17: Hooks

**Files:**
- Create: `frontend/src/hooks/useFriends.ts`
- Create: `frontend/src/hooks/useFriendRequests.ts`
- Create: `frontend/src/hooks/useDirectConversations.ts`
- Create: `frontend/src/hooks/useDirectMessages.ts`
- Create: `frontend/src/hooks/useDirectMessageSocket.ts`

- [ ] **Step 1: useFriends**

```typescript
import { useState, useEffect, useCallback } from 'react';
import { FriendView } from '../types/friendship';
import { friendshipService } from '../services/friendshipService';
import { banService } from '../services/banService';

export function useFriends() {
  const [friends, setFriends] = useState<FriendView[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try { setFriends(await friendshipService.listFriends()); } catch (e: any) { setError(e.message); }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const removeFriend = useCallback(async (userId: string) => {
    await friendshipService.removeFriend(userId);
    setFriends(prev => prev.filter(f => f.userId !== userId));
  }, []);

  const banUser = useCallback(async (userId: string) => {
    await banService.banUser(userId);
    setFriends(prev => prev.filter(f => f.userId !== userId));
  }, []);

  return { friends, error, reload, removeFriend, banUser };
}
```

- [ ] **Step 2: useFriendRequests**

```typescript
import { useState, useEffect, useCallback } from 'react';
import { Friendship } from '../types/friendship';
import { friendshipService } from '../services/friendshipService';

export function useFriendRequests() {
  const [incoming, setIncoming] = useState<Friendship[]>([]);
  const [outgoing, setOutgoing] = useState<Friendship[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [inc, out] = await Promise.all([
        friendshipService.listIncoming(),
        friendshipService.listOutgoing(),
      ]);
      setIncoming(inc);
      setOutgoing(out);
    } catch (e: any) {
      setError(e.message);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const accept = useCallback(async (id: string) => {
    await friendshipService.accept(id);
    setIncoming(prev => prev.filter(r => r.id !== id));
  }, []);

  const reject = useCallback(async (id: string) => {
    await friendshipService.reject(id);
    setIncoming(prev => prev.filter(r => r.id !== id));
  }, []);

  const cancel = useCallback(async (id: string) => {
    await friendshipService.cancel(id);
    setOutgoing(prev => prev.filter(r => r.id !== id));
  }, []);

  const sendRequest = useCallback(async (username: string) => {
    const req = await friendshipService.sendRequest(username);
    if (req.status === 'accepted') {
      await reload(); // went from no relationship / pending → accepted
    } else {
      setOutgoing(prev => [req, ...prev]);
    }
  }, [reload]);

  return { incoming, outgoing, error, sendRequest, accept, reject, cancel, reload };
}
```

- [ ] **Step 3: useDirectConversations**

```typescript
import { useState, useEffect, useCallback } from 'react';
import { ConversationView } from '../types/directMessage';
import { directMessageService } from '../services/directMessageService';

export function useDirectConversations() {
  const [conversations, setConversations] = useState<ConversationView[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try { setConversations(await directMessageService.listConversations()); } catch (e: any) { setError(e.message); }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const bumpOnNewMessage = useCallback((conversationId: string, text: string, at: string) => {
    setConversations(prev => {
      const idx = prev.findIndex(c => c.id === conversationId);
      if (idx < 0) return prev;
      const updated = { ...prev[idx], lastMessage: text, lastMessageAt: at };
      const rest = prev.filter((_, i) => i !== idx);
      return [updated, ...rest];
    });
  }, []);

  return { conversations, error, reload, bumpOnNewMessage };
}
```

- [ ] **Step 4: useDirectMessages** (mirrors useRoomMessages)

```typescript
import { useState, useCallback } from 'react';
import { DirectMessage } from '../types/directMessage';
import { directMessageService } from '../services/directMessageService';

export function useDirectMessages(conversationId: string | undefined) {
  const [messages, setMessages] = useState<DirectMessage[]>([]);
  const [hasMore, setHasMore] = useState(true);
  const [isLoading, setIsLoading] = useState(false);

  const loadInitial = useCallback(async (id: string) => {
    setIsLoading(true);
    try {
      const h = await directMessageService.getHistory(id, undefined, 50);
      setMessages(h);
      setHasMore(h.length === 50);
    } finally { setIsLoading(false); }
  }, []);

  const loadMore = useCallback(async () => {
    if (!conversationId || !hasMore) return;
    setIsLoading(true);
    try {
      const oldest = messages[messages.length - 1];
      const h = await directMessageService.getHistory(conversationId, oldest?.id, 50);
      setMessages(prev => [...prev, ...h]);
      setHasMore(h.length === 50);
    } finally { setIsLoading(false); }
  }, [conversationId, messages, hasMore]);

  const addMessage = useCallback((m: DirectMessage) => {
    setMessages(prev => [m, ...prev]);
  }, []);

  return { messages, hasMore, isLoading, loadInitial, loadMore, addMessage };
}
```

- [ ] **Step 5: useDirectMessageSocket**

```typescript
import { useEffect, useRef } from 'react';
import { websocketService } from '../services/websocketService';
import { DirectMessage } from '../types/directMessage';
import { FriendEvent } from '../types/directMessage';

type DmHandler = (msg: DirectMessage) => void;
type FriendEventHandler = (event: FriendEvent) => void;

export function useDirectMessageSocket(onDm: DmHandler, onFriendEvent: FriendEventHandler) {
  const dmSubRef = useRef<any>(null);
  const feSubRef = useRef<any>(null);

  useEffect(() => {
    let mounted = true;
    const connect = async () => {
      const token = localStorage.getItem('authToken') || '';
      try {
        await websocketService.connect(token);
        if (!mounted) return;
        dmSubRef.current = websocketService.subscribe('/user/queue/dms', (frame) => {
          onDm(JSON.parse(frame.body));
        });
        feSubRef.current = websocketService.subscribe('/user/queue/friend-events', (frame) => {
          onFriendEvent(JSON.parse(frame.body));
        });
      } catch (e) {
        console.error('WebSocket connect failed', e);
      }
    };
    connect();
    return () => {
      mounted = false;
      dmSubRef.current?.unsubscribe();
      feSubRef.current?.unsubscribe();
    };
  }, [onDm, onFriendEvent]);

  const sendDm = (conversationId: string, text: string) => {
    websocketService.send(`/app/dms/${conversationId}/message`, { text });
  };

  return { sendDm };
}
```

- [ ] **Step 6: Extend websocketService.connect to send Authorization header**

Open `frontend/src/services/websocketService.ts`. In the `Client(...)` options, add:

```typescript
connectHeaders: { Authorization: `Bearer ${token}` },
```

That line goes inside the `new Client({...})` object passed to stomp client constructor.

- [ ] **Step 7: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/hooks/ frontend/src/services/websocketService.ts
git commit -m "feat(frontend): friendship/DM/socket hooks

- useFriends: list, remove, ban
- useFriendRequests: incoming/outgoing with accept/reject/cancel + sendRequest
- useDirectConversations: list with bumpOnNewMessage
- useDirectMessages: cursor pagination + addMessage
- useDirectMessageSocket: subscribes /user/queue/dms and /user/queue/friend-events
- websocketService now sends Authorization header on STOMP CONNECT"
```

---

### Task 18: AppSidebar + routing

**Files:**
- Create: `frontend/src/components/AppSidebar.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Create AppSidebar**

```tsx
import React from 'react';
import { Link, useLocation } from 'react-router-dom';

export const AppSidebar: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { pathname } = useLocation();
  const item = (to: string, label: string) => {
    const active = pathname === to || pathname.startsWith(to + '/');
    return (
      <Link to={to} className={`block px-4 py-2 rounded ${active ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100'}`}>
        {label}
      </Link>
    );
  };
  return (
    <div className="flex h-screen">
      <aside className="w-48 border-r bg-white p-4 space-y-1">
        <h2 className="text-lg font-bold mb-4">Chat</h2>
        {item('/rooms', 'Rooms')}
        {item('/friends', 'Friends')}
        {item('/dms', 'Direct Messages')}
      </aside>
      <main className="flex-1 overflow-hidden">{children}</main>
    </div>
  );
};
```

- [ ] **Step 2: Update App.tsx**

In `frontend/src/App.tsx`:
- Import `AppSidebar` and the new pages (`FriendsPage`, `DirectMessagesPage`, `DirectChatPage`)
- Wrap the protected routes (under `AuthGuard`) in `AppSidebar`
- Add new routes:
  - `/friends` → `<FriendsPage />`
  - `/dms` → `<DirectMessagesPage />`
  - `/dms/:conversationId` → `<DirectChatPage />`

Exact integration depends on current App.tsx layout — if routes are declared inline in a `Routes` block wrapped by `AuthGuard`, change:

```tsx
<Route element={<AuthGuard><AppSidebar><Outlet /></AppSidebar></AuthGuard>}>
  <Route path="/rooms" element={<RoomListPage />} />
  <Route path="/rooms/:roomId" element={<ChatPage />} />
  <Route path="/friends" element={<FriendsPage />} />
  <Route path="/dms" element={<DirectMessagesPage />} />
  <Route path="/dms/:conversationId" element={<DirectChatPage />} />
</Route>
```

(Use `Outlet` from react-router-dom to render the matched child route within the sidebar layout.)

- [ ] **Step 3: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/AppSidebar.tsx frontend/src/App.tsx
git commit -m "feat(frontend): AppSidebar + routes for Friends and DMs"
```

---

### Task 19: FriendsPage + components

**Files:**
- Create: `frontend/src/components/SendFriendRequestForm.tsx`
- Create: `frontend/src/components/FriendsList.tsx`
- Create: `frontend/src/components/FriendRequestList.tsx`
- Create: `frontend/src/pages/FriendsPage.tsx`

- [ ] **Step 1: SendFriendRequestForm**

```tsx
import React, { useState } from 'react';

interface Props {
  onSubmit: (username: string) => Promise<void>;
}

export const SendFriendRequestForm: React.FC<Props> = ({ onSubmit }) => {
  const [username, setUsername] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) return;
    setBusy(true); setError(null);
    try { await onSubmit(username.trim()); setUsername(''); }
    catch (e: any) { setError(e?.response?.data?.message || e.message || 'Failed to send request'); }
    finally { setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="flex gap-2 mb-4">
      <input
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        placeholder="Username"
        className="flex-1 border rounded px-3 py-2"
        disabled={busy}
      />
      <button type="submit" disabled={busy || !username.trim()} className="px-4 py-2 bg-blue-500 text-white rounded disabled:bg-gray-400">
        {busy ? 'Sending...' : 'Send request'}
      </button>
      {error && <div className="text-red-500 text-sm">{error}</div>}
    </form>
  );
};
```

- [ ] **Step 2: FriendsList**

```tsx
import React from 'react';
import { FriendView } from '../types/friendship';
import { useNavigate } from 'react-router-dom';
import { directMessageService } from '../services/directMessageService';

interface Props {
  friends: FriendView[];
  onRemove: (userId: string) => void;
  onBan: (userId: string) => void;
}

export const FriendsList: React.FC<Props> = ({ friends, onRemove, onBan }) => {
  const navigate = useNavigate();

  const openDm = async (userId: string) => {
    const conv = await directMessageService.getOrCreateWith(userId);
    navigate(`/dms/${conv.id}`);
  };

  if (friends.length === 0) return <div className="text-gray-500 italic">No friends yet</div>;

  return (
    <ul className="divide-y">
      {friends.map(f => (
        <li key={f.userId} className="flex justify-between items-center py-2">
          <span className="font-medium">{f.username}</span>
          <div className="space-x-2">
            <button onClick={() => openDm(f.userId)} className="px-3 py-1 bg-blue-500 text-white rounded">Message</button>
            <button onClick={() => onRemove(f.userId)} className="px-3 py-1 border rounded">Remove</button>
            <button onClick={() => onBan(f.userId)} className="px-3 py-1 border border-red-500 text-red-600 rounded">Ban</button>
          </div>
        </li>
      ))}
    </ul>
  );
};
```

- [ ] **Step 3: FriendRequestList**

```tsx
import React from 'react';
import { Friendship } from '../types/friendship';

interface Props {
  incoming: Friendship[];
  outgoing: Friendship[];
  onAccept: (id: string) => void;
  onReject: (id: string) => void;
  onCancel: (id: string) => void;
}

export const FriendRequestList: React.FC<Props> = ({ incoming, outgoing, onAccept, onReject, onCancel }) => (
  <div className="space-y-4">
    <section>
      <h3 className="font-semibold mb-2">Incoming requests</h3>
      {incoming.length === 0 ? <p className="text-gray-500 italic">None</p> :
        <ul className="divide-y">
          {incoming.map(r => (
            <li key={r.id} className="flex justify-between py-2">
              <span>Request from user {r.requesterId.slice(0, 8)}</span>
              <div className="space-x-2">
                <button onClick={() => onAccept(r.id)} className="px-3 py-1 bg-green-500 text-white rounded">Accept</button>
                <button onClick={() => onReject(r.id)} className="px-3 py-1 border rounded">Reject</button>
              </div>
            </li>
          ))}
        </ul>}
    </section>
    <section>
      <h3 className="font-semibold mb-2">Outgoing requests</h3>
      {outgoing.length === 0 ? <p className="text-gray-500 italic">None</p> :
        <ul className="divide-y">
          {outgoing.map(r => (
            <li key={r.id} className="flex justify-between py-2">
              <span>To user {r.addresseeId.slice(0, 8)}</span>
              <button onClick={() => onCancel(r.id)} className="px-3 py-1 border rounded">Cancel</button>
            </li>
          ))}
        </ul>}
    </section>
  </div>
);
```

- [ ] **Step 4: FriendsPage**

```tsx
import React from 'react';
import { SendFriendRequestForm } from '../components/SendFriendRequestForm';
import { FriendsList } from '../components/FriendsList';
import { FriendRequestList } from '../components/FriendRequestList';
import { useFriends } from '../hooks/useFriends';
import { useFriendRequests } from '../hooks/useFriendRequests';

export const FriendsPage: React.FC = () => {
  const { friends, removeFriend, banUser, reload: reloadFriends } = useFriends();
  const { incoming, outgoing, sendRequest, accept, reject, cancel } = useFriendRequests();

  const handleAccept = async (id: string) => {
    await accept(id);
    await reloadFriends();
  };

  return (
    <div className="max-w-2xl mx-auto p-6 space-y-6 overflow-y-auto h-full">
      <h1 className="text-2xl font-bold">Friends</h1>
      <SendFriendRequestForm onSubmit={sendRequest} />
      <FriendRequestList incoming={incoming} outgoing={outgoing}
        onAccept={handleAccept} onReject={reject} onCancel={cancel} />
      <hr />
      <h2 className="text-xl font-semibold">Friends list</h2>
      <FriendsList friends={friends} onRemove={removeFriend} onBan={banUser} />
    </div>
  );
};
```

- [ ] **Step 5: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/SendFriendRequestForm.tsx frontend/src/components/FriendsList.tsx \
        frontend/src/components/FriendRequestList.tsx frontend/src/pages/FriendsPage.tsx
git commit -m "feat(frontend): FriendsPage with request form, requests list, friends list"
```

---

### Task 20: DirectMessagesPage + ConversationList

**Files:**
- Create: `frontend/src/components/ConversationList.tsx`
- Create: `frontend/src/pages/DirectMessagesPage.tsx`

- [ ] **Step 1: ConversationList**

```tsx
import React from 'react';
import { ConversationView } from '../types/directMessage';
import { Link } from 'react-router-dom';

interface Props { conversations: ConversationView[]; }

export const ConversationList: React.FC<Props> = ({ conversations }) => {
  if (conversations.length === 0) return <p className="text-gray-500 italic">No direct messages yet. Start one from your Friends page.</p>;
  return (
    <ul className="divide-y border rounded">
      {conversations.map(c => (
        <li key={c.id}>
          <Link to={`/dms/${c.id}`} className="flex justify-between items-center p-3 hover:bg-gray-50">
            <div>
              <div className="font-medium">{c.otherUsername}</div>
              <div className="text-sm text-gray-500 truncate max-w-xs">{c.lastMessage ?? 'No messages yet'}</div>
            </div>
            {c.lastMessageAt && (
              <div className="text-xs text-gray-400">{new Date(c.lastMessageAt).toLocaleString()}</div>
            )}
          </Link>
        </li>
      ))}
    </ul>
  );
};
```

- [ ] **Step 2: DirectMessagesPage**

```tsx
import React from 'react';
import { ConversationList } from '../components/ConversationList';
import { useDirectConversations } from '../hooks/useDirectConversations';

export const DirectMessagesPage: React.FC = () => {
  const { conversations, error } = useDirectConversations();
  return (
    <div className="max-w-2xl mx-auto p-6 space-y-4 overflow-y-auto h-full">
      <h1 className="text-2xl font-bold">Direct Messages</h1>
      {error && <div className="text-red-500">{error}</div>}
      <ConversationList conversations={conversations} />
    </div>
  );
};
```

- [ ] **Step 3: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/ConversationList.tsx frontend/src/pages/DirectMessagesPage.tsx
git commit -m "feat(frontend): DirectMessagesPage with conversation list"
```

---

### Task 21: DirectChatPage

**Files:**
- Create: `frontend/src/pages/DirectChatPage.tsx`

- [ ] **Step 1: DirectChatPage**

```tsx
import React, { useCallback, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { useDirectMessages } from '../hooks/useDirectMessages';
import { useDirectMessageSocket } from '../hooks/useDirectMessageSocket';
import { DirectMessage } from '../types/directMessage';

export const DirectChatPage: React.FC = () => {
  const { conversationId } = useParams<{ conversationId: string }>();
  const { messages, hasMore, isLoading, loadInitial, loadMore, addMessage } = useDirectMessages(conversationId);

  const onDm = useCallback((dm: DirectMessage & { senderUsername?: string }) => {
    if (dm.conversationId === conversationId) addMessage(dm);
  }, [conversationId, addMessage]);

  const { sendDm } = useDirectMessageSocket(onDm, () => {});

  useEffect(() => { if (conversationId) loadInitial(conversationId); }, [conversationId, loadInitial]);

  const handleSend = (text: string) => { if (conversationId) sendDm(conversationId, text); };

  // Adapt DirectMessage → Message prop shape for MessageList (if MessageList expects .username).
  // We map senderId to a placeholder since MessageList renders .username; our DmEvent payload has senderUsername.
  const adapted = messages.map((m: any) => ({
    id: m.id,
    roomId: m.conversationId,
    userId: m.senderId,
    username: m.senderUsername ?? m.senderId.slice(0, 8),
    text: m.text,
    createdAt: m.createdAt,
  }));

  return (
    <div className="flex flex-col h-full">
      <div className="bg-white shadow p-4 border-b">
        <h1 className="text-xl font-bold">Direct Message</h1>
      </div>
      <MessageList messages={adapted} isLoading={isLoading} hasMore={hasMore} onLoadMore={loadMore} />
      <MessageInput onSend={handleSend} disabled={!conversationId} />
    </div>
  );
};
```

- [ ] **Step 2: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/pages/DirectChatPage.tsx
git commit -m "feat(frontend): DirectChatPage reuses MessageList/MessageInput"
```

---

### Task 22: RoomMembersPanel + ChatPage update

**Files:**
- Create: `frontend/src/components/RoomMembersPanel.tsx`
- Modify: `frontend/src/pages/ChatPage.tsx`
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java` (add `GET /api/rooms/{id}/members`)
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoomService.java` (listMembers method)

- [ ] **Step 1: Backend — add members endpoint**

In `ChatRoomService.java`, add:

```java
public java.util.List<com.hackathon.features.users.User> listMembers(java.util.UUID roomId) {
  return roomMemberService.getMembers(roomId).stream()
      .map(userId -> userService.getUserById(userId))
      .toList();
}
```

You may need to inject `UserService` into `ChatRoomService` — update its constructor args (Lombok `@RequiredArgsConstructor` will pick up the added field).

In `ChatRoomController.java`, add:

```java
@GetMapping("/{id}/members")
public ResponseEntity<java.util.List<RoomMemberView>> listMembers(@PathVariable java.util.UUID id) {
  return ResponseEntity.ok(
      chatRoomService.listMembers(id).stream()
          .map(u -> new RoomMemberView(u.getId(), u.getUsername()))
          .toList());
}

record RoomMemberView(java.util.UUID userId, String username) {}
```

- [ ] **Step 2: Frontend — roomService extension**

In `frontend/src/services/roomService.ts` add:

```typescript
listMembers: async (roomId: string): Promise<{ userId: string; username: string }[]> => {
  return (await axios.get(`/api/rooms/${roomId}/members`)).data;
},
```

- [ ] **Step 3: RoomMembersPanel component**

```tsx
import React, { useEffect, useState } from 'react';
import { roomService } from '../services/roomService';
import { friendshipService } from '../services/friendshipService';

interface Props { roomId: string; currentUserId: string; }

export const RoomMembersPanel: React.FC<Props> = ({ roomId, currentUserId }) => {
  const [members, setMembers] = useState<{ userId: string; username: string }[]>([]);
  const [friendIds, setFriendIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    roomService.listMembers(roomId).then(setMembers);
    friendshipService.listFriends().then(fs => setFriendIds(new Set(fs.map(f => f.userId))));
  }, [roomId]);

  const sendRequest = async (username: string, userId: string) => {
    await friendshipService.sendRequest(username);
    setFriendIds(prev => new Set([...prev, userId]));
  };

  return (
    <aside className="w-56 border-l bg-white p-4 overflow-y-auto">
      <h3 className="font-semibold mb-2">Members</h3>
      <ul className="space-y-2">
        {members.map(m => {
          const isMe = m.userId === currentUserId;
          const isFriend = friendIds.has(m.userId);
          return (
            <li key={m.userId} className="flex justify-between items-center text-sm">
              <span>{m.username}{isMe && ' (you)'}</span>
              {!isMe && !isFriend && (
                <button onClick={() => sendRequest(m.username, m.userId)}
                        className="text-xs px-2 py-1 border rounded hover:bg-blue-50">
                  Add friend
                </button>
              )}
            </li>
          );
        })}
      </ul>
    </aside>
  );
};
```

- [ ] **Step 4: Update ChatPage to render panel**

In `frontend/src/pages/ChatPage.tsx`, insert `RoomMembersPanel` as a right-side column. Because layout details vary, the shape is:

```tsx
<div className="flex h-full">
  <div className="flex-1 flex flex-col">
    {/* existing header + MessageList + MessageInput */}
  </div>
  {roomId && currentUserId && <RoomMembersPanel roomId={roomId} currentUserId={currentUserId} />}
</div>
```

Get `currentUserId` from the existing auth hook (`useAuth` or similar that already decodes JWT). If no such hook exists, add a tiny one that reads `localStorage.getItem('authToken')` and decodes the `sub` claim (JWT middle segment, base64) to get the UUID.

- [ ] **Step 5: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton/backend && ./gradlew build
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/ChatRoomService.java \
        backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java \
        frontend/src/services/roomService.ts \
        frontend/src/components/RoomMembersPanel.tsx \
        frontend/src/pages/ChatPage.tsx
git commit -m "feat(rooms): GET /api/rooms/{id}/members + RoomMembersPanel with Add Friend"
```

---

## Section G: Integration Tests

### Task 23: Backend end-to-end integration test

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/integration/FriendsAndDmsFlowIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.bans.UserBanService;
import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FriendsAndDmsFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired FriendshipService friendshipService;
  @Autowired UserBanService userBanService;
  @Autowired ConversationService conversationService;
  @Autowired DirectMessageService directMessageService;

  private User register(String email, String username, String password) {
    return userService.registerUser(email, username, password);
  }

  @Test
  void fullHappyPath() {
    User alice = register("alice+" + System.nanoTime() + "@x", "alice" + System.nanoTime(), "pw12345678");
    User bob = register("bob+" + System.nanoTime() + "@x", "bob" + System.nanoTime(), "pw12345678");

    // Alice sends friend request to Bob
    Friendship req = friendshipService.sendRequest(alice.getId(), bob.getUsername());
    assertEquals(Friendship.STATUS_PENDING, req.getStatus());

    // Bob accepts
    Friendship accepted = friendshipService.accept(bob.getId(), req.getId());
    assertEquals(Friendship.STATUS_ACCEPTED, accepted.getStatus());

    // Alice sends DM to Bob
    DirectMessage dm1 = directMessageService.sendToUser(alice.getId(), bob.getId(), "hey bob");
    assertEquals("hey bob", dm1.getText());
    assertEquals(alice.getId(), dm1.getSenderId());

    // Conversation id
    var conv = conversationService.getOrCreate(alice.getId(), bob.getId());
    assertEquals(dm1.getConversationId(), conv.getId());

    // Bob replies
    DirectMessage dm2 = directMessageService.send(bob.getId(), conv.getId(), "hey alice");
    assertEquals("hey alice", dm2.getText());

    // History returns both
    List<DirectMessage> history = directMessageService.getHistory(conv.getId(), null, 50);
    assertEquals(2, history.size());

    // Bob bans Alice
    userBanService.ban(bob.getId(), alice.getId());

    // Alice cannot send DM now
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(alice.getId(), conv.getId(), "still there?"));

    // Friendship is gone
    assertTrue(friendshipService.listAccepted(alice.getId()).isEmpty());
    assertTrue(friendshipService.listAccepted(bob.getId()).isEmpty());
  }

  @Test
  void autoAcceptOnInverseRequest() {
    User a = register("a" + System.nanoTime() + "@x", "au" + System.nanoTime(), "pw12345678");
    User b = register("b" + System.nanoTime() + "@x", "bu" + System.nanoTime(), "pw12345678");

    Friendship first = friendshipService.sendRequest(a.getId(), b.getUsername());
    assertEquals(Friendship.STATUS_PENDING, first.getStatus());

    Friendship second = friendshipService.sendRequest(b.getId(), a.getUsername());
    assertEquals(Friendship.STATUS_ACCEPTED, second.getStatus());
  }
}
```

- [ ] **Step 2: Run**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'FriendsAndDmsFlowIntegrationTest'
```

Expected: 2 scenarios PASS.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/test/java/com/hackathon/features/integration/FriendsAndDmsFlowIntegrationTest.java
git commit -m "test: end-to-end Friends+DMs flow (happy path, auto-accept, ban-terminates-friendship)"
```

---

### Task 24: Frontend end-to-end smoke test

**Files:**
- Create: `frontend/src/__tests__/FriendsAndDmsFlow.test.tsx`

- [ ] **Step 1: Write smoke test**

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { FriendsPage } from '../pages/FriendsPage';
import { friendshipService } from '../services/friendshipService';

vi.mock('../services/friendshipService');
vi.mock('../services/banService');
vi.mock('../services/directMessageService');

describe('FriendsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (friendshipService.listFriends as any).mockResolvedValue([]);
    (friendshipService.listIncoming as any).mockResolvedValue([]);
    (friendshipService.listOutgoing as any).mockResolvedValue([]);
  });

  it('renders empty state', async () => {
    render(<MemoryRouter><FriendsPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/No friends yet/i)).toBeInTheDocument());
  });

  it('sends a friend request', async () => {
    (friendshipService.sendRequest as any).mockResolvedValue({
      id: 'r1', requesterId: 'me', addresseeId: 'u2', status: 'pending', createdAt: '', updatedAt: '',
    });

    render(<MemoryRouter><FriendsPage /></MemoryRouter>);

    const input = await screen.findByPlaceholderText('Username');
    fireEvent.change(input, { target: { value: 'bob' } });
    fireEvent.click(screen.getByText('Send request'));

    await waitFor(() => expect(friendshipService.sendRequest).toHaveBeenCalledWith('bob'));
  });
});
```

- [ ] **Step 2: Run**

```bash
cd /src/ai_hakaton/frontend && npm test -- --run FriendsAndDmsFlow
```

Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/__tests__/FriendsAndDmsFlow.test.tsx
git commit -m "test(frontend): smoke test FriendsPage empty state and send-request"
```

---

## Verification Checklist

Before considering Feature #3 complete:

- [ ] Backend: `./gradlew test` — all tests pass (including Feature #1/#2 regression)
- [ ] Frontend: `npm test -- --run` — all tests pass
- [ ] Docker: `docker compose up --build -d` succeeds; all 3 services healthy
- [ ] Flyway V3 applied (check logs for "Successfully applied 1 migration ... version 3")
- [ ] Manual smoke: register 2 users → login → send friend request → accept → open DM → send messages both directions → verify real-time delivery in browser dev tools (STOMP frames)
- [ ] Ban flow: user A bans user B → B's DM attempts 403 → friendship gone from both sides
- [ ] Auto-accept: A→B pending, B→A sends request → both become friends without extra step
- [ ] Add-Friend from room user list: open a room, click Add Friend on a member → request appears in their FriendsPage
- [ ] TIMESTAMPTZ verified: `SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='public' AND data_type LIKE 'timestamp%'` — all rows say `timestamp with time zone`
- [ ] Feature #2 controllers resolve real user IDs (no more `UUID.randomUUID()`)

---

## Notes

- The `sub` claim in JWTs becomes a UUID string after this feature. Any existing JWTs issued before the rollover become invalid — acceptable for hackathon dev.
- `@WithMockUser` tests require mocking `UserService.getUserByUsername("user")` because controllers call it as a fallback when `Authentication.getDetails()` is not a UUID.
- The frontend auth hook should be updated to decode the new UUID-subject JWT. If it already just stores the token and reads `sub`, no change needed; if it parsed sub as integer, update to string.
- The WebSocket `ChannelInterceptor` parses the Authorization header on STOMP CONNECT; the frontend sends it via `connectHeaders` (added in Task 17).

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-friends-and-dms.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — fresh subagent per task, two-stage review between tasks, fast iteration. Uses superpowers:subagent-driven-development.

**2. Inline Execution** — tasks run in this session with checkpoints. Uses superpowers:executing-plans.

**Which approach?**
