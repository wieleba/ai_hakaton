# Feature #4: Private Rooms, Room Moderation & Invitations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the rooms story from requirements §2.4: private rooms visible only to invitees, owner+admin roles with moderation actions (kick, ban, promote, demote), room deletion, and invitation lifecycle.

**Architecture:** Extend the existing `features/rooms/` package. Store admin role on `room_members.role` (one source of truth per room+user pair). Two new tables `room_invitations` and `room_bans` with `ON DELETE CASCADE` from `chat_rooms` so owner-deletes-room is one DB statement. Split responsibilities into `ChatRoomService` (lifecycle), new `RoomModerationService` (kick/ban/admin role), new `RoomInvitationService` (invitation lifecycle). Owner implicitly admin via `chat_rooms.owner_id`; `isAdmin` short-circuits on `isOwner`.

**Tech Stack:** Spring Boot 3.5.12 / Java 25, Gradle 9.4.1, PostgreSQL 15, Flyway V4, Lombok, Instancio, JUnit 5. React 19 + TypeScript + Vite, axios, Vitest, Playwright. Reference spec: `docs/superpowers/specs/2026-04-18-room-moderation-and-invitations-design.md`.

**⚠️ Rule (backend/CLAUDE.md + frontend/CLAUDE.md):** Every commit must build cleanly. Before committing, run `./gradlew compileJava compileTestJava` (backend) or `npm run build` (frontend). Commit ONLY when tests pass.

---

## File Structure

### Backend — new files

```
backend/src/main/resources/db/migration/
  V4__room_moderation_and_invitations.sql       (migration)

backend/src/main/java/com/hackathon/features/rooms/
  RoomBan.java                                  (JPA entity)
  RoomBanRepository.java
  RoomInvitation.java                           (JPA entity)
  RoomInvitationRepository.java
  RoomModerationService.java                    (kick, promote, demote, unban, listBans)
  RoomModerationController.java
  RoomInvitationService.java
  RoomInvitationController.java

backend/src/test/java/com/hackathon/features/rooms/
  RoomModerationServiceTest.java
  RoomModerationControllerTest.java
  RoomInvitationServiceTest.java
  RoomInvitationControllerTest.java
backend/src/test/java/com/hackathon/features/integration/
  RoomModerationFlowIntegrationTest.java
```

### Backend — modified files

```
features/rooms/
  RoomMember.java                               (add `role` field)
  RoomMemberRepository.java                     (add findByUserIdOrderByJoinedAt for /mine)
  RoomMemberService.java                        (add isAdmin, isOwner, listMembersWithRoles)
  ChatRoom.java                                 (no schema change; already has visibility)
  ChatRoomService.java                          (createRoom accepts visibility; listMyRooms; deleteRoom; ban check on join)
  ChatRoomController.java                       (accept visibility; /mine; DELETE; enhance /members with role)
  ChatRoomServiceTest.java                      (extend)
  ChatRoomControllerTest.java                   (extend)
```

### Frontend — new files

```
frontend/src/types/
  roomModeration.ts                             (RoomRole, RoomMemberView, RoomBan, RoomInvitation)
frontend/src/services/
  roomInvitationService.ts
frontend/src/hooks/
  useMyRooms.ts
  useRoomInvitations.ts
  useRoomMembersWithRole.ts
  useRoomAdminActions.ts
frontend/src/components/
  RoomInvitationList.tsx
  InviteUserModal.tsx
  BanListPanel.tsx
  DeleteRoomDialog.tsx
frontend/e2e/
  room-moderation.spec.ts                       (Playwright e2e)
```

### Frontend — modified files

```
frontend/src/types/room.ts                      (add visibility helper if needed)
frontend/src/services/roomService.ts            (add listMyRooms, deleteRoom, moderation actions)
frontend/src/components/RoomCreateModal.tsx     (visibility picker)
frontend/src/components/RoomMembersPanel.tsx    (role badges, admin controls)
frontend/src/pages/RoomListPage.tsx             (tabbed Public | My rooms)
frontend/src/pages/ChatPage.tsx                 (Delete Room button, forbidden-room handling)
```

---

## Task Overview

| # | Task | Section |
|---|------|---------|
| 1 | V4 Flyway migration (role column + room_invitations + room_bans tables) | A: schema |
| 2 | Entities + repositories (RoomMember.role, RoomBan, RoomInvitation) | A: entities |
| 3 | RoomMemberService permission helpers (isAdmin, isOwner, listMembersWithRoles) | B: services |
| 4 | ChatRoomService extensions (visibility on create, listMyRooms, deleteRoom, ban check on join) | B: services |
| 5 | RoomModerationService TDD (kick, promote, demote, unban, listBans) | B: services |
| 6 | RoomInvitationService TDD (invite, listIncoming, listOutgoingForRoom, accept, decline, cancel) | B: services |
| 7 | ChatRoomController enhancements + tests | C: controllers |
| 8 | RoomModerationController + tests | C: controllers |
| 9 | RoomInvitationController + tests | C: controllers |
| 10 | Backend end-to-end integration test | D: integration |
| 11 | Frontend types + roomService extensions + roomInvitationService | E: frontend services |
| 12 | Frontend hooks (useMyRooms, useRoomInvitations, useRoomMembersWithRole, useRoomAdminActions) | E: frontend hooks |
| 13 | RoomCreateModal — visibility picker | F: components |
| 14 | RoomListPage — tabbed layout with RoomInvitationList | F: components |
| 15 | InviteUserModal | F: components |
| 16 | RoomMembersPanel admin controls + role badges | F: components |
| 17 | BanListPanel | F: components |
| 18 | DeleteRoomDialog + Delete Room button + ChatPage forbidden-room handling | F: components |
| 19 | Playwright e2e — full room-moderation lifecycle | G: e2e |

---

## Section A: Schema & Entities

### Task 1: V4 Flyway Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__room_moderation_and_invitations.sql`

- [ ] **Step 1: Create the migration SQL**

Exact contents for `backend/src/main/resources/db/migration/V4__room_moderation_and_invitations.sql`:

```sql
-- Add role column to room_members (existing rows default to 'member').
ALTER TABLE room_members
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'member'
    CHECK (role IN ('member', 'admin'));

-- Invitations live per (room, invitee). Accept deletes row + inserts membership;
-- decline deletes row. No historical audit needed.
CREATE TABLE room_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES users(id),
    invitee_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_room_invitee UNIQUE (room_id, invitee_id),
    CONSTRAINT no_self_invite CHECK (inviter_id <> invitee_id)
);

CREATE INDEX idx_room_invitations_invitee ON room_invitations(invitee_id);

-- Room bans: "kick" atomically deletes room_members row + inserts here.
-- Preserves banned_by + banned_at so admins can see who banned each user.
CREATE TABLE room_bans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    banned_user_id UUID NOT NULL REFERENCES users(id),
    banned_by_id UUID NOT NULL REFERENCES users(id),
    banned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_room_banned_user UNIQUE (room_id, banned_user_id)
);

CREATE INDEX idx_room_bans_banned_user ON room_bans(banned_user_id);
```

- [ ] **Step 2: Verify backend still compiles and tests green**

Hibernate `ddl-auto=validate` accepts extra columns in the DB that aren't mapped in entities, and `'member'` is the DEFAULT so existing inserts (which don't set `role`) still succeed. No entity changes yet — they land in Task 2.

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava compileTestJava
./gradlew test
```

Expected: BUILD SUCCESSFUL on both, all existing tests pass. The migration will run the next time the app starts, which is what Task 2's tests exercise.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/resources/db/migration/V4__room_moderation_and_invitations.sql
git commit -m "database: V4 migration — room moderation + invitations

- room_members gets role column ('member' | 'admin'); default 'member'
- room_invitations: per (room_id, invitee_id), cascades from chat_rooms
- room_bans: preserves banned_by_id + banned_at; cascades from chat_rooms
- All CHECK constraints / UNIQUE constraints enforced at DB layer"
```

---

### Task 2: Entities + Repositories

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/rooms/RoomMember.java`
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomBan.java`
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomBanRepository.java`
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomInvitation.java`
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomInvitationRepository.java`

- [ ] **Step 1: Add `role` field to RoomMember**

Replace `backend/src/main/java/com/hackathon/features/rooms/RoomMember.java` with:

```java
package com.hackathon.features.rooms;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "room_members",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"room_id", "user_id"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {
  public static final String ROLE_MEMBER = "member";
  public static final String ROLE_ADMIN = "admin";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String role = ROLE_MEMBER;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private OffsetDateTime joinedAt;
}
```

- [ ] **Step 2: Create RoomBan entity**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomBan.java`:

```java
package com.hackathon.features.rooms;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "room_bans",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"room_id", "banned_user_id"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBan {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "banned_user_id", nullable = false)
  private UUID bannedUserId;

  @Column(name = "banned_by_id", nullable = false)
  private UUID bannedById;

  @CreationTimestamp
  @Column(name = "banned_at", nullable = false, updatable = false)
  private OffsetDateTime bannedAt;
}
```

- [ ] **Step 3: Create RoomBanRepository**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomBanRepository.java`:

```java
package com.hackathon.features.rooms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomBanRepository extends JpaRepository<RoomBan, UUID> {
  boolean existsByRoomIdAndBannedUserId(UUID roomId, UUID bannedUserId);

  Optional<RoomBan> findByRoomIdAndBannedUserId(UUID roomId, UUID bannedUserId);

  List<RoomBan> findByRoomIdOrderByBannedAtDesc(UUID roomId);

  void deleteByRoomIdAndBannedUserId(UUID roomId, UUID bannedUserId);
}
```

- [ ] **Step 4: Create RoomInvitation entity**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomInvitation.java`:

```java
package com.hackathon.features.rooms;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "room_invitations",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"room_id", "invitee_id"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomInvitation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "inviter_id", nullable = false)
  private UUID inviterId;

  @Column(name = "invitee_id", nullable = false)
  private UUID inviteeId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
```

- [ ] **Step 5: Create RoomInvitationRepository**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomInvitationRepository.java`:

```java
package com.hackathon.features.rooms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, UUID> {
  boolean existsByRoomIdAndInviteeId(UUID roomId, UUID inviteeId);

  Optional<RoomInvitation> findByRoomIdAndInviteeId(UUID roomId, UUID inviteeId);

  List<RoomInvitation> findByRoomIdOrderByCreatedAtDesc(UUID roomId);

  List<RoomInvitation> findByInviteeIdOrderByCreatedAtDesc(UUID inviteeId);
}
```

- [ ] **Step 6: Compile + run all existing tests**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava compileTestJava
./gradlew test
```

Expected: BUILD SUCCESSFUL, all existing tests pass. The RoomMember entity now has a `role` field (default `"member"`), which matches the V4-migrated schema.

- [ ] **Step 7: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/RoomMember.java \
        backend/src/main/java/com/hackathon/features/rooms/RoomBan.java \
        backend/src/main/java/com/hackathon/features/rooms/RoomBanRepository.java \
        backend/src/main/java/com/hackathon/features/rooms/RoomInvitation.java \
        backend/src/main/java/com/hackathon/features/rooms/RoomInvitationRepository.java
git commit -m "feat(rooms): add role to RoomMember; new RoomBan + RoomInvitation entities"
```

---

## Section B: Services

### Task 3: RoomMemberService permission helpers

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/rooms/RoomMemberService.java`

- [ ] **Step 1: Add isAdmin / isOwner / listMembersWithRoles**

Replace `backend/src/main/java/com/hackathon/features/rooms/RoomMemberService.java` with:

```java
package com.hackathon.features.rooms;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomMemberService {
  private final RoomMemberRepository roomMemberRepository;
  private final ChatRoomRepository chatRoomRepository;

  public boolean isMember(UUID roomId, UUID userId) {
    return roomMemberRepository.existsByRoomIdAndUserId(roomId, userId);
  }

  /**
   * True if the user is the room owner, or a member with role='admin'. Used as
   * the single permission gate for moderation actions.
   */
  public boolean isAdmin(UUID roomId, UUID userId) {
    if (isOwner(roomId, userId)) return true;
    return roomMemberRepository
        .findByRoomIdAndUserId(roomId, userId)
        .map(m -> RoomMember.ROLE_ADMIN.equals(m.getRole()))
        .orElse(false);
  }

  public boolean isOwner(UUID roomId, UUID userId) {
    return chatRoomRepository
        .findById(roomId)
        .map(room -> room.getOwnerId().equals(userId))
        .orElse(false);
  }

  public void addMember(UUID roomId, UUID userId) {
    RoomMember member =
        RoomMember.builder()
            .roomId(roomId)
            .userId(userId)
            .role(RoomMember.ROLE_MEMBER)
            .build();
    roomMemberRepository.save(member);
  }

  public void removeMember(UUID roomId, UUID userId) {
    roomMemberRepository.deleteByRoomIdAndUserId(roomId, userId);
  }

  public List<UUID> getMembers(UUID roomId) {
    return roomMemberRepository.findByRoomId(roomId).stream().map(RoomMember::getUserId).toList();
  }

  public List<RoomMember> listMembersWithRoles(UUID roomId) {
    return roomMemberRepository.findByRoomId(roomId);
  }

  public void setRole(UUID roomId, UUID userId, String role) {
    if (!RoomMember.ROLE_MEMBER.equals(role) && !RoomMember.ROLE_ADMIN.equals(role)) {
      throw new IllegalArgumentException("Invalid role: " + role);
    }
    RoomMember member =
        roomMemberRepository
            .findByRoomIdAndUserId(roomId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Not a member of this room"));
    member.setRole(role);
    roomMemberRepository.save(member);
  }
}
```

- [ ] **Step 2: Compile + run tests**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava compileTestJava
./gradlew test
```

Expected: BUILD SUCCESSFUL. Existing `RoomMemberServiceTest` tests still green — they use the original `isMember`/`addMember`/`removeMember`/`getMembers`. We added new methods without changing old signatures. Also the constructor gained a `ChatRoomRepository` argument; the test uses `@Mock`/`@InjectMocks` style — if the test uses `new RoomMemberService(mockRepo)`, it needs updating. Check with:

```bash
grep -n "new RoomMemberService" backend/src/test/java/com/hackathon/features/rooms/*.java
```

If the test uses `new RoomMemberService(roomMemberRepository)`, update it to `new RoomMemberService(roomMemberRepository, chatRoomRepository)` and add `@Mock ChatRoomRepository chatRoomRepository;`.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/RoomMemberService.java \
        backend/src/test/java/com/hackathon/features/rooms/RoomMemberServiceTest.java
git commit -m "feat(rooms): add isAdmin/isOwner/listMembersWithRoles/setRole helpers"
```

---

### Task 4: ChatRoomService extensions (TDD)

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoomService.java`
- Modify: `backend/src/test/java/com/hackathon/features/rooms/ChatRoomServiceTest.java`
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoomRepository.java` (add queries)
- Modify: `backend/src/main/java/com/hackathon/features/rooms/RoomMemberRepository.java` (add query)

- [ ] **Step 1: Add repository queries**

Open `backend/src/main/java/com/hackathon/features/rooms/ChatRoomRepository.java` and add (keep existing methods):

```java
  @org.springframework.data.jpa.repository.Query(
      "SELECT r FROM ChatRoom r WHERE r.id IN "
          + "(SELECT m.roomId FROM RoomMember m WHERE m.userId = :userId) "
          + "ORDER BY r.updatedAt DESC")
  java.util.List<ChatRoom> findRoomsWhereUserIsMember(java.util.UUID userId);
```

Open `backend/src/main/java/com/hackathon/features/rooms/RoomMemberRepository.java` — existing `findByRoomId` / `findByUserId` / `findByRoomIdAndUserId` / `existsByRoomIdAndUserId` / `deleteByRoomIdAndUserId` should already cover needs. No change required.

- [ ] **Step 2: Extend `ChatRoomServiceTest` with new tests**

Open `backend/src/test/java/com/hackathon/features/rooms/ChatRoomServiceTest.java`. Add `@Mock RoomBanRepository roomBanRepository;` alongside existing mocks, and pass it into the constructor call in `@BeforeEach`. Add these test methods to the class:

```java
  @Test
  void createRoom_defaultsToPublicWhenVisibilityNull() {
    UUID userId = create(UUID.class);
    when(chatRoomRepository.existsByName("r1")).thenReturn(false);
    when(chatRoomRepository.save(any(ChatRoom.class)))
        .thenAnswer(inv -> inv.getArgument(0, ChatRoom.class));

    ChatRoom result = service.createRoom("r1", null, userId, null);

    assertEquals("public", result.getVisibility());
  }

  @Test
  void createRoom_acceptsPrivateVisibility() {
    UUID userId = create(UUID.class);
    when(chatRoomRepository.existsByName("r1")).thenReturn(false);
    when(chatRoomRepository.save(any(ChatRoom.class)))
        .thenAnswer(inv -> inv.getArgument(0, ChatRoom.class));

    ChatRoom result = service.createRoom("r1", null, userId, "private");

    assertEquals("private", result.getVisibility());
  }

  @Test
  void createRoom_rejectsInvalidVisibility() {
    UUID userId = create(UUID.class);
    when(chatRoomRepository.existsByName("r1")).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.createRoom("r1", null, userId, "secret"));
  }

  @Test
  void joinRoom_rejectsPrivateRoom() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("private");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    assertThrows(
        IllegalArgumentException.class, () -> service.joinRoom(roomId, userId));
  }

  @Test
  void joinRoom_rejectsWhenBanned() {
    UUID roomId = create(UUID.class);
    UUID userId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("public");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, userId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.joinRoom(roomId, userId));
  }

  @Test
  void listMyRooms_returnsRoomsWhereUserIsMember() {
    UUID userId = create(UUID.class);
    ChatRoom r1 = create(ChatRoom.class);
    ChatRoom r2 = create(ChatRoom.class);
    when(chatRoomRepository.findRoomsWhereUserIsMember(userId)).thenReturn(List.of(r1, r2));

    assertEquals(2, service.listMyRooms(userId).size());
  }

  @Test
  void deleteRoom_onlyOwnerMayDelete() {
    UUID roomId = create(UUID.class);
    UUID ownerId = create(UUID.class);
    UUID someoneElse = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setOwnerId(ownerId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    assertThrows(
        IllegalArgumentException.class, () -> service.deleteRoom(roomId, someoneElse));
  }

  @Test
  void deleteRoom_ownerSucceeds() {
    UUID roomId = create(UUID.class);
    UUID ownerId = create(UUID.class);
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setOwnerId(ownerId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    service.deleteRoom(roomId, ownerId);

    verify(chatRoomRepository).delete(room);
  }
```

Add import `import java.util.List;` if not present. Also:

```java
  @Mock private RoomBanRepository roomBanRepository;
```

And update `@BeforeEach` setup (existing):

```java
  service = new ChatRoomService(chatRoomRepository, roomMemberService, userService, roomBanRepository);
```

- [ ] **Step 3: Run the new tests — they fail to compile**

```bash
cd /src/ai_hakaton/backend
./gradlew test --tests 'ChatRoomServiceTest' 2>&1 | tail -10
```

Expected: compilation errors on `createRoom` (signature mismatch — 4th arg), `listMyRooms`, `deleteRoom`, `joinRoom` + ban check.

- [ ] **Step 4: Implement the extensions**

Replace `backend/src/main/java/com/hackathon/features/rooms/ChatRoomService.java` with:

```java
package com.hackathon.features.rooms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
  private static final String VISIBILITY_PUBLIC = "public";
  private static final String VISIBILITY_PRIVATE = "private";

  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberService roomMemberService;
  private final UserService userService;
  private final RoomBanRepository roomBanRepository;

  @Transactional
  public ChatRoom createRoom(String name, String description, UUID userId, String visibility) {
    if (chatRoomRepository.existsByName(name)) {
      throw new IllegalArgumentException("Room name already exists");
    }
    String vis = visibility == null ? VISIBILITY_PUBLIC : visibility;
    if (!VISIBILITY_PUBLIC.equals(vis) && !VISIBILITY_PRIVATE.equals(vis)) {
      throw new IllegalArgumentException("Invalid visibility: " + vis);
    }
    ChatRoom room =
        ChatRoom.builder()
            .name(name)
            .description(description)
            .ownerId(userId)
            .visibility(vis)
            .build();
    ChatRoom savedRoom = chatRoomRepository.save(room);
    roomMemberService.addMember(savedRoom.getId(), userId);
    return savedRoom;
  }

  public Page<ChatRoom> listPublicRooms(int page, int limit) {
    return chatRoomRepository.findByVisibility(VISIBILITY_PUBLIC, PageRequest.of(page, limit));
  }

  public List<ChatRoom> listMyRooms(UUID userId) {
    return chatRoomRepository.findRoomsWhereUserIsMember(userId);
  }

  @Transactional
  public void joinRoom(UUID roomId, UUID userId) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (!VISIBILITY_PUBLIC.equals(room.getVisibility())) {
      throw new IllegalArgumentException("Cannot join private room");
    }
    if (roomBanRepository.existsByRoomIdAndBannedUserId(roomId, userId)) {
      throw new IllegalArgumentException("Cannot join this room");
    }
    if (roomMemberService.isMember(roomId, userId)) {
      return; // idempotent for re-entry after leave
    }
    roomMemberService.addMember(roomId, userId);
  }

  public void leaveRoom(UUID roomId, UUID userId) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (room.getOwnerId().equals(userId)) {
      throw new IllegalArgumentException("Owner cannot leave their own room");
    }
    roomMemberService.removeMember(roomId, userId);
  }

  @Transactional
  public void deleteRoom(UUID roomId, UUID userId) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (!room.getOwnerId().equals(userId)) {
      throw new IllegalArgumentException("Only the owner may delete this room");
    }
    chatRoomRepository.delete(room);
  }

  public ChatRoom getRoomById(UUID roomId) {
    return chatRoomRepository
        .findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
  }

  public List<User> listMembers(UUID roomId) {
    return roomMemberService.getMembers(roomId).stream()
        .map(userService::getUserById)
        .toList();
  }
}
```

- [ ] **Step 5: Run tests — should pass**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. `ChatRoomServiceTest` includes both existing tests and the new ones.

Note: the existing `createRoom(name, description, userId)` call sites now must pass a 4th arg. Callers:
- `ChatRoomController` still uses the old 3-arg signature → Task 7 fixes
- Existing tests `testCreateRoom` + `testCreateRoomDuplicateName` call `service.createRoom(roomName, null, userId)` → update them to `service.createRoom(roomName, null, userId, null)`.

Make those test edits before committing.

- [ ] **Step 6: Update existing `testCreateRoom` and `testCreateRoomDuplicateName`**

In `ChatRoomServiceTest.java`, each `service.createRoom(roomName, null, userId)` becomes `service.createRoom(roomName, null, userId, null)`.

- [ ] **Step 7: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/ChatRoomService.java \
        backend/src/main/java/com/hackathon/features/rooms/ChatRoomRepository.java \
        backend/src/test/java/com/hackathon/features/rooms/ChatRoomServiceTest.java
git commit -m "feat(rooms): ChatRoomService visibility + listMyRooms + deleteRoom + ban check

- createRoom accepts visibility param (default 'public'); validates enum
- joinRoom rejects private rooms + rooms the caller is banned from
- listMyRooms returns rooms where user is member (public + private)
- deleteRoom enforces owner-only; cascades via FK"
```

**Note:** `ChatRoomController` still calls `createRoom(name, description, userId)` (3-arg) — Task 7 fixes that. If `compileJava` fails after this commit because the controller doesn't match, that would violate the compile-cleanly rule. Before committing, update the controller's call site to `createRoom(request.name(), request.description(), currentUserId(authentication), null)` — a minimal change that preserves existing behavior; full visibility support lands in Task 7.

---

### Task 5: RoomModerationService with TDD

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/rooms/RoomModerationServiceTest.java`
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomModerationService.java`

- [ ] **Step 1: Write RoomModerationServiceTest**

Create `backend/src/test/java/com/hackathon/features/rooms/RoomModerationServiceTest.java`:

```java
package com.hackathon.features.rooms;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RoomModerationServiceTest {
  @Mock private RoomMemberService roomMemberService;
  @Mock private RoomMemberRepository roomMemberRepository;
  @Mock private RoomBanRepository roomBanRepository;
  @Mock private ChatRoomRepository chatRoomRepository;

  private RoomModerationService service;
  private UUID roomId;
  private UUID adminId;
  private UUID targetId;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service =
        new RoomModerationService(
            roomMemberService, roomMemberRepository, roomBanRepository, chatRoomRepository);
    roomId = create(UUID.class);
    adminId = create(UUID.class);
    targetId = create(UUID.class);
    ownerId = create(UUID.class);
  }

  private void stubRoom(UUID withOwnerId) {
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setOwnerId(withOwnerId);
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
  }

  // --- kick ---

  @Test
  void kick_adminRemovesMemberAndCreatesBanRow() {
    stubRoom(ownerId);
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);
    when(roomBanRepository.save(any(RoomBan.class)))
        .thenAnswer(inv -> inv.getArgument(0, RoomBan.class));

    service.kick(roomId, adminId, targetId);

    verify(roomMemberRepository).deleteByRoomIdAndUserId(roomId, targetId);
    verify(roomBanRepository).save(any(RoomBan.class));
  }

  @Test
  void kick_rejectsNonAdmin() {
    stubRoom(ownerId);
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> service.kick(roomId, adminId, targetId));
    verify(roomMemberRepository, never()).deleteByRoomIdAndUserId(any(), any());
  }

  @Test
  void kick_rejectsKickingOwner() {
    stubRoom(targetId); // target is the owner
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.kick(roomId, adminId, targetId));
  }

  @Test
  void kick_rejectsSelfKick() {
    stubRoom(ownerId);
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.kick(roomId, adminId, adminId));
  }

  // --- promote / demote ---

  @Test
  void promoteAdmin_adminGrantsRole() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, targetId)).thenReturn(true);

    service.promoteAdmin(roomId, adminId, targetId);

    verify(roomMemberService).setRole(roomId, targetId, RoomMember.ROLE_ADMIN);
  }

  @Test
  void promoteAdmin_rejectsIfTargetNotMember() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, targetId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.promoteAdmin(roomId, adminId, targetId));
  }

  @Test
  void demoteAdmin_adminRevokesRole() {
    stubRoom(ownerId);
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);
    when(roomMemberRepository.findByRoomIdAndUserId(roomId, targetId))
        .thenReturn(
            Optional.of(RoomMember.builder().roomId(roomId).userId(targetId).role(RoomMember.ROLE_ADMIN).build()));

    service.demoteAdmin(roomId, adminId, targetId);

    verify(roomMemberService).setRole(roomId, targetId, RoomMember.ROLE_MEMBER);
  }

  @Test
  void demoteAdmin_rejectsDemotingOwner() {
    stubRoom(targetId); // target IS the owner
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.demoteAdmin(roomId, adminId, targetId));
  }

  @Test
  void demoteAdmin_rejectsSelfDemote() {
    stubRoom(ownerId);
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.demoteAdmin(roomId, adminId, adminId));
  }

  // --- unban ---

  @Test
  void unban_adminDeletesBanRow() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    service.unban(roomId, adminId, targetId);

    verify(roomBanRepository).deleteByRoomIdAndBannedUserId(roomId, targetId);
  }

  @Test
  void unban_rejectsNonAdmin() {
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> service.unban(roomId, adminId, targetId));
  }
}
```

- [ ] **Step 2: Run test — expect compile failure on RoomModerationService**

```bash
cd /src/ai_hakaton/backend
./gradlew test --tests 'RoomModerationServiceTest' 2>&1 | tail -10
```

Expected: "cannot find symbol: class RoomModerationService".

- [ ] **Step 3: Implement RoomModerationService**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomModerationService.java`:

```java
package com.hackathon.features.rooms;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomModerationService {
  private final RoomMemberService roomMemberService;
  private final RoomMemberRepository roomMemberRepository;
  private final RoomBanRepository roomBanRepository;
  private final ChatRoomRepository chatRoomRepository;

  @Transactional
  public void kick(UUID roomId, UUID callerId, UUID targetId) {
    requireAdmin(roomId, callerId);
    if (callerId.equals(targetId)) {
      throw new IllegalArgumentException("Cannot kick yourself — use Leave instead");
    }
    ChatRoom room = requireRoom(roomId);
    if (room.getOwnerId().equals(targetId)) {
      throw new IllegalArgumentException("Cannot kick the room owner");
    }
    roomMemberRepository.deleteByRoomIdAndUserId(roomId, targetId);
    RoomBan ban =
        RoomBan.builder()
            .roomId(roomId)
            .bannedUserId(targetId)
            .bannedById(callerId)
            .build();
    roomBanRepository.save(ban);
  }

  @Transactional
  public void promoteAdmin(UUID roomId, UUID callerId, UUID targetId) {
    requireAdmin(roomId, callerId);
    if (!roomMemberService.isMember(roomId, targetId)) {
      throw new IllegalArgumentException("Target is not a member of this room");
    }
    roomMemberService.setRole(roomId, targetId, RoomMember.ROLE_ADMIN);
  }

  @Transactional
  public void demoteAdmin(UUID roomId, UUID callerId, UUID targetId) {
    requireAdmin(roomId, callerId);
    if (callerId.equals(targetId)) {
      throw new IllegalArgumentException("Cannot demote yourself");
    }
    ChatRoom room = requireRoom(roomId);
    if (room.getOwnerId().equals(targetId)) {
      throw new IllegalArgumentException("Cannot demote the room owner");
    }
    roomMemberService.setRole(roomId, targetId, RoomMember.ROLE_MEMBER);
  }

  @Transactional
  public void unban(UUID roomId, UUID callerId, UUID targetId) {
    requireAdmin(roomId, callerId);
    roomBanRepository.deleteByRoomIdAndBannedUserId(roomId, targetId);
  }

  public List<RoomBan> listBans(UUID roomId, UUID callerId) {
    requireAdmin(roomId, callerId);
    return roomBanRepository.findByRoomIdOrderByBannedAtDesc(roomId);
  }

  private void requireAdmin(UUID roomId, UUID callerId) {
    if (!roomMemberService.isAdmin(roomId, callerId)) {
      throw new IllegalArgumentException("Admin privilege required");
    }
  }

  private ChatRoom requireRoom(UUID roomId) {
    return chatRoomRepository
        .findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
  }
}
```

- [ ] **Step 4: Run tests — all pass**

```bash
./gradlew test --tests 'RoomModerationServiceTest'
./gradlew test
```

Expected: all 11 new tests pass; full suite still green.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/RoomModerationService.java \
        backend/src/test/java/com/hackathon/features/rooms/RoomModerationServiceTest.java
git commit -m "feat(rooms): RoomModerationService (kick, promote, demote, unban, listBans)

- Every admin action goes through requireAdmin(roomId, callerId)
- kick: delete room_members row + insert room_bans row in one @Transactional
- promote: idempotent; target must be a member
- demote: target must not be owner; caller must not be self
- unban: delete room_bans row (no-op if absent)
- listBans: admin-only"
```

---

### Task 6: RoomInvitationService with TDD

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/rooms/RoomInvitationServiceTest.java`
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomInvitationService.java`

- [ ] **Step 1: Write RoomInvitationServiceTest**

Create `backend/src/test/java/com/hackathon/features/rooms/RoomInvitationServiceTest.java`:

```java
package com.hackathon.features.rooms;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RoomInvitationServiceTest {
  @Mock private RoomInvitationRepository roomInvitationRepository;
  @Mock private RoomMemberService roomMemberService;
  @Mock private RoomBanRepository roomBanRepository;
  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private UserService userService;

  private RoomInvitationService service;
  private UUID roomId;
  private UUID inviterId;
  private UUID inviteeId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service =
        new RoomInvitationService(
            roomInvitationRepository,
            roomMemberService,
            roomBanRepository,
            chatRoomRepository,
            userService);
    roomId = create(UUID.class);
    inviterId = create(UUID.class);
    inviteeId = create(UUID.class);
  }

  private void stubPrivateRoom() {
    ChatRoom room = create(ChatRoom.class);
    room.setId(roomId);
    room.setVisibility("private");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
  }

  // --- invite ---

  @Test
  void invite_creates_pendingInvitation() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(false);
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(false);
    when(roomInvitationRepository.existsByRoomIdAndInviteeId(roomId, inviteeId)).thenReturn(false);
    when(roomInvitationRepository.save(any(RoomInvitation.class)))
        .thenAnswer(inv -> inv.getArgument(0, RoomInvitation.class));

    RoomInvitation inv = service.invite(roomId, inviterId, "bob");

    assertEquals(roomId, inv.getRoomId());
    assertEquals(inviterId, inv.getInviterId());
    assertEquals(inviteeId, inv.getInviteeId());
  }

  @Test
  void invite_rejectsPublicRoom() {
    ChatRoom room = create(ChatRoom.class);
    room.setVisibility("public");
    when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsSelfInvite() {
    stubPrivateRoom();
    when(userService.getUserByUsername("me"))
        .thenReturn(User.builder().id(inviterId).username("me").build());

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "me"));
  }

  @Test
  void invite_rejectsNonMemberInviter() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsInviteeAlreadyMember() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsBannedInvitee() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(false);
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  @Test
  void invite_rejectsDuplicatePending() {
    stubPrivateRoom();
    when(userService.getUserByUsername("bob"))
        .thenReturn(User.builder().id(inviteeId).username("bob").build());
    when(roomMemberService.isMember(roomId, inviterId)).thenReturn(true);
    when(roomMemberService.isMember(roomId, inviteeId)).thenReturn(false);
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(false);
    when(roomInvitationRepository.existsByRoomIdAndInviteeId(roomId, inviteeId)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service.invite(roomId, inviterId, "bob"));
  }

  // --- accept / decline / cancel ---

  @Test
  void accept_addsMembershipAndDeletesInvitation() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(false);

    service.accept(invId, inviteeId);

    verify(roomMemberService).addMember(roomId, inviteeId);
    verify(roomInvitationRepository).delete(inv);
  }

  @Test
  void accept_rejectsNonInvitee() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));

    assertThrows(
        IllegalArgumentException.class, () -> service.accept(invId, create(UUID.class)));
  }

  @Test
  void accept_rechecksBan() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.accept(invId, inviteeId));
  }

  @Test
  void decline_deletesInvitation() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));

    service.decline(invId, inviteeId);

    verify(roomInvitationRepository).delete(inv);
    verify(roomMemberService, never()).addMember(any(), any());
  }

  @Test
  void cancel_allowsInviter() {
    UUID invId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));

    service.cancel(invId, inviterId);

    verify(roomInvitationRepository).delete(inv);
  }

  @Test
  void cancel_allowsAdmin() {
    UUID invId = create(UUID.class);
    UUID adminId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomMemberService.isAdmin(roomId, adminId)).thenReturn(true);

    service.cancel(invId, adminId);

    verify(roomInvitationRepository).delete(inv);
  }

  @Test
  void cancel_rejectsOthers() {
    UUID invId = create(UUID.class);
    UUID strangerId = create(UUID.class);
    RoomInvitation inv =
        RoomInvitation.builder()
            .id(invId)
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build();
    when(roomInvitationRepository.findById(invId)).thenReturn(Optional.of(inv));
    when(roomMemberService.isAdmin(roomId, strangerId)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.cancel(invId, strangerId));
  }

  @Test
  void listMyIncoming_returnsInvitations() {
    when(roomInvitationRepository.findByInviteeIdOrderByCreatedAtDesc(inviteeId))
        .thenReturn(
            List.of(
                RoomInvitation.builder()
                    .id(create(UUID.class))
                    .roomId(roomId)
                    .inviteeId(inviteeId)
                    .build()));

    assertEquals(1, service.listMyIncoming(inviteeId).size());
  }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
cd /src/ai_hakaton/backend
./gradlew test --tests 'RoomInvitationServiceTest' 2>&1 | tail -10
```

Expected: "cannot find symbol: class RoomInvitationService".

- [ ] **Step 3: Implement RoomInvitationService**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomInvitationService.java`:

```java
package com.hackathon.features.rooms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomInvitationService {
  private final RoomInvitationRepository roomInvitationRepository;
  private final RoomMemberService roomMemberService;
  private final RoomBanRepository roomBanRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final UserService userService;

  @Transactional
  public RoomInvitation invite(UUID roomId, UUID inviterId, String inviteeUsername) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (!"private".equals(room.getVisibility())) {
      throw new IllegalArgumentException("Only private rooms need invitations");
    }
    User invitee = userService.getUserByUsername(inviteeUsername);
    UUID inviteeId = invitee.getId();

    if (inviteeId.equals(inviterId)) {
      throw new IllegalArgumentException("Cannot invite yourself");
    }
    if (!roomMemberService.isMember(roomId, inviterId)) {
      throw new IllegalArgumentException("Only members may invite others");
    }
    if (roomMemberService.isMember(roomId, inviteeId)) {
      throw new IllegalArgumentException("User is already a member of this room");
    }
    if (roomBanRepository.existsByRoomIdAndBannedUserId(roomId, inviteeId)) {
      throw new IllegalArgumentException("Cannot invite a banned user");
    }
    if (roomInvitationRepository.existsByRoomIdAndInviteeId(roomId, inviteeId)) {
      throw new IllegalArgumentException("An invitation for this user already exists");
    }

    return roomInvitationRepository.save(
        RoomInvitation.builder()
            .roomId(roomId)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .build());
  }

  @Transactional
  public void accept(UUID invitationId, UUID currentUserId) {
    RoomInvitation inv = requireInvitation(invitationId);
    if (!inv.getInviteeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the invitee may accept");
    }
    if (roomBanRepository.existsByRoomIdAndBannedUserId(inv.getRoomId(), currentUserId)) {
      throw new IllegalArgumentException("You are banned from this room");
    }
    roomMemberService.addMember(inv.getRoomId(), currentUserId);
    roomInvitationRepository.delete(inv);
  }

  @Transactional
  public void decline(UUID invitationId, UUID currentUserId) {
    RoomInvitation inv = requireInvitation(invitationId);
    if (!inv.getInviteeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the invitee may decline");
    }
    roomInvitationRepository.delete(inv);
  }

  @Transactional
  public void cancel(UUID invitationId, UUID currentUserId) {
    RoomInvitation inv = requireInvitation(invitationId);
    boolean isInviter = inv.getInviterId().equals(currentUserId);
    boolean isAdmin = roomMemberService.isAdmin(inv.getRoomId(), currentUserId);
    if (!isInviter && !isAdmin) {
      throw new IllegalArgumentException("Only the inviter or a room admin may cancel");
    }
    roomInvitationRepository.delete(inv);
  }

  public List<RoomInvitation> listMyIncoming(UUID currentUserId) {
    return roomInvitationRepository.findByInviteeIdOrderByCreatedAtDesc(currentUserId);
  }

  public List<RoomInvitation> listOutgoingForRoom(UUID roomId, UUID currentUserId) {
    if (!roomMemberService.isMember(roomId, currentUserId)) {
      throw new IllegalArgumentException("Only members may view a room's outgoing invitations");
    }
    return roomInvitationRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
  }

  private RoomInvitation requireInvitation(UUID id) {
    return roomInvitationRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));
  }
}
```

- [ ] **Step 4: Run tests — all pass**

```bash
./gradlew test --tests 'RoomInvitationServiceTest'
./gradlew test
```

Expected: 14 new tests pass + full suite green.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/RoomInvitationService.java \
        backend/src/test/java/com/hackathon/features/rooms/RoomInvitationServiceTest.java
git commit -m "feat(rooms): RoomInvitationService

- invite: enforce private-room, member-inviter, not-already-member,
  not-banned, no-duplicate-pending
- accept: invitee only, re-check ban, create membership + delete invite
- decline: invitee deletes the invite row
- cancel: inviter or admin
- listMyIncoming / listOutgoingForRoom"
```

---

## Section C: REST Controllers

### Task 7: ChatRoomController enhancements + tests

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java`
- Modify: `backend/src/test/java/com/hackathon/features/rooms/ChatRoomControllerTest.java`

- [ ] **Step 1: Replace ChatRoomController**

Replace `backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java` with:

```java
package com.hackathon.features.rooms;

import com.hackathon.features.users.UserService;
import java.util.List;
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
  private final RoomMemberService roomMemberService;

  record CreateRoomRequest(String name, String description, String visibility) {}

  record RoomMemberView(UUID userId, String username, String role, boolean isOwner) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping
  public ResponseEntity<ChatRoom> createRoom(
      @RequestBody CreateRoomRequest request, Authentication authentication) {
    ChatRoom room =
        chatRoomService.createRoom(
            request.name(),
            request.description(),
            currentUserId(authentication),
            request.visibility());
    return ResponseEntity.ok(room);
  }

  @GetMapping
  public ResponseEntity<Page<ChatRoom>> listPublicRooms(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(chatRoomService.listPublicRooms(page, limit));
  }

  @GetMapping("/mine")
  public ResponseEntity<List<ChatRoom>> listMyRooms(Authentication authentication) {
    return ResponseEntity.ok(chatRoomService.listMyRooms(currentUserId(authentication)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ChatRoom> getRoom(@PathVariable UUID id, Authentication authentication) {
    ChatRoom room = chatRoomService.getRoomById(id);
    // For private rooms, return 404 to non-members to avoid leaking existence.
    if ("private".equals(room.getVisibility())
        && !roomMemberService.isMember(id, currentUserId(authentication))) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(room);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteRoom(@PathVariable UUID id, Authentication authentication) {
    chatRoomService.deleteRoom(id, currentUserId(authentication));
    return ResponseEntity.noContent().build();
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

  @GetMapping("/{id}/members")
  public ResponseEntity<List<RoomMemberView>> listMembers(@PathVariable UUID id) {
    ChatRoom room = chatRoomService.getRoomById(id);
    UUID ownerId = room.getOwnerId();
    List<RoomMemberView> views =
        roomMemberService.listMembersWithRoles(id).stream()
            .map(
                m -> {
                  var user = userService.getUserById(m.getUserId());
                  return new RoomMemberView(
                      m.getUserId(), user.getUsername(), m.getRole(), m.getUserId().equals(ownerId));
                })
            .toList();
    return ResponseEntity.ok(views);
  }
}
```

- [ ] **Step 2: Update ChatRoomControllerTest**

Open `backend/src/test/java/com/hackathon/features/rooms/ChatRoomControllerTest.java`. Add:

```java
  @MockBean RoomMemberService roomMemberService;
```

(If it's already `@MockBean`'d through the `ChatRoomService`, keep it; otherwise add it alongside the other mock beans.)

The existing `testCreateRoom` test sends `{"name":"test-room"}` — that still works with the new controller since `visibility` is optional. The returned room is now a `ChatRoom` JSON, same shape as before.

Add new tests inside the class:

```java
  @Test
  @WithMockUser(username = "user")
  void createRoom_passesVisibility() throws Exception {
    UUID roomId = UUID.randomUUID();
    ChatRoom room = new ChatRoom();
    room.setId(roomId);
    room.setName("secret");
    room.setVisibility("private");
    when(chatRoomService.createRoom(eq("secret"), isNull(), any(UUID.class), eq("private")))
        .thenReturn(room);

    mockMvc
        .perform(
            post("/api/rooms")
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"secret\",\"visibility\":\"private\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("private"));
  }

  @Test
  @WithMockUser(username = "user")
  void listMyRooms() throws Exception {
    ChatRoom r = new ChatRoom();
    r.setName("r1");
    when(chatRoomService.listMyRooms(any(UUID.class))).thenReturn(List.of(r));

    mockMvc
        .perform(get("/api/rooms/mine"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("r1"));
  }

  @Test
  @WithMockUser(username = "user")
  void deleteRoom_returns204() throws Exception {
    UUID id = UUID.randomUUID();
    doNothing().when(chatRoomService).deleteRoom(eq(id), any(UUID.class));

    mockMvc
        .perform(delete("/api/rooms/{id}", id).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(username = "user")
  void listMembers_includesRoleAndIsOwner() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    ChatRoom room = new ChatRoom();
    room.setId(roomId);
    room.setOwnerId(ownerId);
    when(chatRoomService.getRoomById(roomId)).thenReturn(room);
    when(roomMemberService.listMembersWithRoles(roomId))
        .thenReturn(
            List.of(
                RoomMember.builder()
                    .roomId(roomId)
                    .userId(ownerId)
                    .role(RoomMember.ROLE_MEMBER)
                    .build(),
                RoomMember.builder()
                    .roomId(roomId)
                    .userId(memberId)
                    .role(RoomMember.ROLE_ADMIN)
                    .build()));
    when(userService.getUserById(ownerId))
        .thenReturn(com.hackathon.features.users.User.builder().id(ownerId).username("boss").build());
    when(userService.getUserById(memberId))
        .thenReturn(com.hackathon.features.users.User.builder().id(memberId).username("mod").build());

    mockMvc
        .perform(get("/api/rooms/{id}/members", roomId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].isOwner").value(true))
        .andExpect(jsonPath("$[0].role").value("member"))
        .andExpect(jsonPath("$[1].isOwner").value(false))
        .andExpect(jsonPath("$[1].role").value("admin"));
  }
```

Ensure these imports are in place:

```java
import static org.mockito.ArgumentMatchers.isNull;
import java.util.List;
```

- [ ] **Step 3: Compile + test**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava compileTestJava
./gradlew test
```

Expected: all tests green.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/ChatRoomController.java \
        backend/src/test/java/com/hackathon/features/rooms/ChatRoomControllerTest.java
git commit -m "feat(rooms): ChatRoomController enhancements

- POST /api/rooms now accepts visibility in request body
- GET /api/rooms/mine returns rooms where user is a member
- DELETE /api/rooms/{id} deletes the room (owner-only, enforced by service)
- GET /api/rooms/{id} returns 404 to non-members of private rooms
- GET /api/rooms/{id}/members returns RoomMemberView with role + isOwner"
```

---

### Task 8: RoomModerationController + tests

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomModerationController.java`
- Create: `backend/src/test/java/com/hackathon/features/rooms/RoomModerationControllerTest.java`

- [ ] **Step 1: Write RoomModerationControllerTest**

Create `backend/src/test/java/com/hackathon/features/rooms/RoomModerationControllerTest.java`:

```java
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
class RoomModerationControllerTest {
  @Autowired MockMvc mvc;
  @MockBean RoomModerationService roomModerationService;
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
  void kickMember() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(delete("/api/rooms/{r}/members/{u}", roomId, targetId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomModerationService).kick(eq(roomId), eq(meId), eq(targetId));
  }

  @Test
  @WithMockUser(username = "user")
  void promoteAdmin() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(
            post("/api/rooms/{r}/admins", roomId)
                .with(csrf())
                .contentType("application/json")
                .content("{\"userId\":\"" + targetId + "\"}"))
        .andExpect(status().isNoContent());
    verify(roomModerationService).promoteAdmin(eq(roomId), eq(meId), eq(targetId));
  }

  @Test
  @WithMockUser(username = "user")
  void demoteAdmin() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(delete("/api/rooms/{r}/admins/{u}", roomId, targetId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomModerationService).demoteAdmin(eq(roomId), eq(meId), eq(targetId));
  }

  @Test
  @WithMockUser(username = "user")
  void listBans() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID bannedId = UUID.randomUUID();
    UUID byId = UUID.randomUUID();
    when(roomModerationService.listBans(eq(roomId), eq(meId)))
        .thenReturn(
            List.of(
                RoomBan.builder()
                    .roomId(roomId)
                    .bannedUserId(bannedId)
                    .bannedById(byId)
                    .bannedAt(OffsetDateTime.now())
                    .build()));
    when(userService.getUserById(bannedId))
        .thenReturn(User.builder().id(bannedId).username("spammer").build());
    when(userService.getUserById(byId))
        .thenReturn(User.builder().id(byId).username("mod").build());

    mvc.perform(get("/api/rooms/{r}/bans", roomId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].bannedUsername").value("spammer"))
        .andExpect(jsonPath("$[0].bannedByUsername").value("mod"));
  }

  @Test
  @WithMockUser(username = "user")
  void unbanMember() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    mvc.perform(delete("/api/rooms/{r}/bans/{u}", roomId, targetId).with(csrf()))
        .andExpect(status().isNoContent());
    verify(roomModerationService).unban(eq(roomId), eq(meId), eq(targetId));
  }
}
```

- [ ] **Step 2: Implement RoomModerationController**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomModerationController.java`:

```java
package com.hackathon.features.rooms;

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
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class RoomModerationController {
  private final RoomModerationService roomModerationService;
  private final UserService userService;

  record AdminRequest(UUID userId) {}

  record BanView(
      UUID bannedUserId,
      String bannedUsername,
      UUID bannedById,
      String bannedByUsername,
      OffsetDateTime bannedAt) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @DeleteMapping("/members/{userId}")
  public ResponseEntity<Void> kick(
      @PathVariable UUID roomId,
      @PathVariable UUID userId,
      Authentication authentication) {
    roomModerationService.kick(roomId, currentUserId(authentication), userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/admins")
  public ResponseEntity<Void> promoteAdmin(
      @PathVariable UUID roomId,
      @RequestBody AdminRequest body,
      Authentication authentication) {
    roomModerationService.promoteAdmin(roomId, currentUserId(authentication), body.userId());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/admins/{userId}")
  public ResponseEntity<Void> demoteAdmin(
      @PathVariable UUID roomId,
      @PathVariable UUID userId,
      Authentication authentication) {
    roomModerationService.demoteAdmin(roomId, currentUserId(authentication), userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/bans")
  public ResponseEntity<List<BanView>> listBans(
      @PathVariable UUID roomId, Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<BanView> views =
        roomModerationService.listBans(roomId, me).stream()
            .map(
                b -> {
                  User banned = userService.getUserById(b.getBannedUserId());
                  User by = userService.getUserById(b.getBannedById());
                  return new BanView(
                      b.getBannedUserId(),
                      banned.getUsername(),
                      b.getBannedById(),
                      by.getUsername(),
                      b.getBannedAt());
                })
            .toList();
    return ResponseEntity.ok(views);
  }

  @DeleteMapping("/bans/{userId}")
  public ResponseEntity<Void> unban(
      @PathVariable UUID roomId,
      @PathVariable UUID userId,
      Authentication authentication) {
    roomModerationService.unban(roomId, currentUserId(authentication), userId);
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 3: Compile + test**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava compileTestJava
./gradlew test --tests 'RoomModerationControllerTest'
./gradlew test
```

Expected: 5 new tests pass; full suite green.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/RoomModerationController.java \
        backend/src/test/java/com/hackathon/features/rooms/RoomModerationControllerTest.java
git commit -m "feat(rooms): RoomModerationController

- DELETE /api/rooms/{r}/members/{u}     (kick = ban)
- POST /api/rooms/{r}/admins body {userId}  (promote)
- DELETE /api/rooms/{r}/admins/{u}      (demote)
- GET /api/rooms/{r}/bans               (list bans; BanView with usernames)
- DELETE /api/rooms/{r}/bans/{u}        (unban)
- Each endpoint returns 204 No Content on success"
```

---

### Task 9: RoomInvitationController + tests

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/rooms/RoomInvitationController.java`
- Create: `backend/src/test/java/com/hackathon/features/rooms/RoomInvitationControllerTest.java`

- [ ] **Step 1: Write RoomInvitationControllerTest**

Create `backend/src/test/java/com/hackathon/features/rooms/RoomInvitationControllerTest.java`:

```java
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
  @MockBean com.hackathon.features.rooms.ChatRoomRepository chatRoomRepository;
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
```

- [ ] **Step 2: Implement RoomInvitationController**

Create `backend/src/main/java/com/hackathon/features/rooms/RoomInvitationController.java`:

```java
package com.hackathon.features.rooms;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RoomInvitationController {
  private final RoomInvitationService roomInvitationService;
  private final ChatRoomRepository chatRoomRepository;
  private final UserService userService;

  record InviteRequest(String username) {}

  record InvitationView(
      UUID id,
      UUID roomId,
      String roomName,
      UUID inviterId,
      String inviterUsername,
      OffsetDateTime createdAt) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @PostMapping("/api/rooms/{roomId}/invitations")
  public ResponseEntity<RoomInvitation> invite(
      @PathVariable UUID roomId,
      @RequestBody InviteRequest body,
      Authentication authentication) {
    RoomInvitation inv =
        roomInvitationService.invite(roomId, currentUserId(authentication), body.username());
    return ResponseEntity.status(HttpStatus.CREATED).body(inv);
  }

  @GetMapping("/api/rooms/{roomId}/invitations")
  public ResponseEntity<List<RoomInvitation>> listRoomOutgoing(
      @PathVariable UUID roomId, Authentication authentication) {
    return ResponseEntity.ok(
        roomInvitationService.listOutgoingForRoom(roomId, currentUserId(authentication)));
  }

  @DeleteMapping("/api/rooms/{roomId}/invitations/{invitationId}")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID roomId,
      @PathVariable UUID invitationId,
      Authentication authentication) {
    roomInvitationService.cancel(invitationId, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/invitations")
  public ResponseEntity<List<InvitationView>> listMyIncoming(Authentication authentication) {
    UUID me = currentUserId(authentication);
    List<InvitationView> views =
        roomInvitationService.listMyIncoming(me).stream()
            .map(
                inv -> {
                  ChatRoom room = chatRoomRepository.findById(inv.getRoomId()).orElse(null);
                  User inviter = userService.getUserById(inv.getInviterId());
                  return new InvitationView(
                      inv.getId(),
                      inv.getRoomId(),
                      room != null ? room.getName() : "(unknown)",
                      inv.getInviterId(),
                      inviter.getUsername(),
                      inv.getCreatedAt());
                })
            .toList();
    return ResponseEntity.ok(views);
  }

  @PostMapping("/api/invitations/{invitationId}/accept")
  public ResponseEntity<Void> accept(
      @PathVariable UUID invitationId, Authentication authentication) {
    roomInvitationService.accept(invitationId, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/invitations/{invitationId}/decline")
  public ResponseEntity<Void> decline(
      @PathVariable UUID invitationId, Authentication authentication) {
    roomInvitationService.decline(invitationId, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 3: Compile + test**

```bash
cd /src/ai_hakaton/backend
./gradlew compileJava compileTestJava
./gradlew test --tests 'RoomInvitationControllerTest'
./gradlew test
```

Expected: 5 new tests pass; full suite green.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/rooms/RoomInvitationController.java \
        backend/src/test/java/com/hackathon/features/rooms/RoomInvitationControllerTest.java
git commit -m "feat(rooms): RoomInvitationController

- POST /api/rooms/{r}/invitations (invite by username)
- GET /api/rooms/{r}/invitations (outgoing per-room, members only)
- DELETE /api/rooms/{r}/invitations/{i} (cancel, inviter or admin)
- GET /api/invitations (my inbox; InvitationView resolves roomName + inviterUsername)
- POST /api/invitations/{i}/accept
- POST /api/invitations/{i}/decline"
```

---

## Section D: Integration Test

### Task 10: End-to-end lifecycle test

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/integration/RoomModerationFlowIntegrationTest.java`

- [ ] **Step 1: Write the integration test**

Create `backend/src/test/java/com/hackathon/features/integration/RoomModerationFlowIntegrationTest.java`:

```java
package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.rooms.RoomInvitation;
import com.hackathon.features.rooms.RoomInvitationService;
import com.hackathon.features.rooms.RoomMemberService;
import com.hackathon.features.rooms.RoomModerationService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RoomModerationFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired RoomMemberService roomMemberService;
  @Autowired RoomInvitationService roomInvitationService;
  @Autowired RoomModerationService roomModerationService;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void fullLifecycle() {
    User owner = register("owner");
    User bob = register("bob");
    User carol = register("carol");

    // Owner creates a private room
    ChatRoom room =
        chatRoomService.createRoom("secret-" + System.nanoTime(), null, owner.getId(), "private");
    assertEquals("private", room.getVisibility());
    assertTrue(roomMemberService.isOwner(room.getId(), owner.getId()));
    assertTrue(roomMemberService.isAdmin(room.getId(), owner.getId()));

    // Owner invites Bob
    RoomInvitation invBob =
        roomInvitationService.invite(room.getId(), owner.getId(), bob.getUsername());
    assertEquals(1, roomInvitationService.listMyIncoming(bob.getId()).size());

    // Bob accepts
    roomInvitationService.accept(invBob.getId(), bob.getId());
    assertTrue(roomMemberService.isMember(room.getId(), bob.getId()));
    assertEquals(0, roomInvitationService.listMyIncoming(bob.getId()).size());

    // Bob invites Carol (must be a member to invite)
    RoomInvitation invCarol =
        roomInvitationService.invite(room.getId(), bob.getId(), carol.getUsername());
    roomInvitationService.accept(invCarol.getId(), carol.getId());

    // Owner promotes Bob to admin
    roomModerationService.promoteAdmin(room.getId(), owner.getId(), bob.getId());
    assertTrue(roomMemberService.isAdmin(room.getId(), bob.getId()));

    // Bob (admin) kicks Carol
    roomModerationService.kick(room.getId(), bob.getId(), carol.getId());
    assertFalse(roomMemberService.isMember(room.getId(), carol.getId()));
    // Carol is now in the ban list; she cannot be invited again
    assertThrows(
        IllegalArgumentException.class,
        () ->
            roomInvitationService.invite(room.getId(), owner.getId(), carol.getUsername()));

    // Owner unbans Carol
    roomModerationService.unban(room.getId(), owner.getId(), carol.getId());
    // Now she can be invited again
    RoomInvitation invCarol2 =
        roomInvitationService.invite(room.getId(), owner.getId(), carol.getUsername());
    roomInvitationService.accept(invCarol2.getId(), carol.getId());

    // Owner demotes Bob — Bob must not be able to kick anyone now
    roomModerationService.demoteAdmin(room.getId(), owner.getId(), bob.getId());
    assertFalse(roomMemberService.isAdmin(room.getId(), bob.getId()));
    assertThrows(
        IllegalArgumentException.class,
        () -> roomModerationService.kick(room.getId(), bob.getId(), carol.getId()));

    // Owner deletes the room — cascades to members/invitations/bans
    chatRoomService.deleteRoom(room.getId(), owner.getId());
    assertFalse(roomMemberService.isMember(room.getId(), owner.getId()));
    assertFalse(roomMemberService.isMember(room.getId(), bob.getId()));
    assertFalse(roomMemberService.isMember(room.getId(), carol.getId()));
  }

  @Test
  void cannotJoinPrivateRoomWithoutInvitation() {
    User owner = register("a");
    User outsider = register("b");
    ChatRoom room =
        chatRoomService.createRoom(
            "closed-" + System.nanoTime(), null, owner.getId(), "private");

    assertThrows(
        IllegalArgumentException.class,
        () -> chatRoomService.joinRoom(room.getId(), outsider.getId()));
  }

  @Test
  void bannedUserCannotJoinPublicRoom() {
    User owner = register("a");
    User troll = register("b");
    ChatRoom room =
        chatRoomService.createRoom(
            "public-" + System.nanoTime(), null, owner.getId(), "public");
    // Troll joins, owner kicks (= bans)
    chatRoomService.joinRoom(room.getId(), troll.getId());
    roomModerationService.kick(room.getId(), owner.getId(), troll.getId());

    assertThrows(
        IllegalArgumentException.class,
        () -> chatRoomService.joinRoom(room.getId(), troll.getId()));
  }
}
```

- [ ] **Step 2: Run the integration test**

```bash
cd /src/ai_hakaton/backend
./gradlew test --tests 'RoomModerationFlowIntegrationTest'
./gradlew test
```

Expected: 3 integration scenarios pass; full suite green.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/test/java/com/hackathon/features/integration/RoomModerationFlowIntegrationTest.java
git commit -m "test: full room moderation + invitation flow

- Create private room → invite → accept → invite a third user
- Promote admin → admin kicks → assert ban prevents re-invite
- Owner unbans → re-invite → accept → demote admin
- Verify demoted admin can't moderate
- Owner deletes room → cascade wipes members/invitations/bans
- Plus: private-without-invite and banned-can't-rejoin-public scenarios"
```

---

## Section E: Frontend — Types, Services, Hooks

### Task 11: Frontend types + service extensions

**Files:**
- Create: `frontend/src/types/roomModeration.ts`
- Create: `frontend/src/services/roomInvitationService.ts`
- Modify: `frontend/src/services/roomService.ts`
- Modify: `frontend/src/types/room.ts` (ensure `visibility` is part of ChatRoom type)

- [ ] **Step 1: Create `frontend/src/types/roomModeration.ts`**

```typescript
import type { ChatRoom } from './room';

export type RoomRole = 'member' | 'admin';

export interface RoomMemberView {
  userId: string;
  username: string;
  role: RoomRole;
  isOwner: boolean;
}

export interface RoomBan {
  bannedUserId: string;
  bannedUsername: string;
  bannedById: string;
  bannedByUsername: string;
  bannedAt: string;
}

export interface RoomInvitation {
  id: string;
  roomId: string;
  roomName: string;
  inviterId: string;
  inviterUsername: string;
  createdAt: string;
}

export type { ChatRoom };
```

- [ ] **Step 2: Verify `ChatRoom` in `frontend/src/types/room.ts`**

Confirm `ChatRoom` has `visibility: 'public' | 'private'`. It already does from Feature #2 — skip if present, otherwise add.

- [ ] **Step 3: Extend `frontend/src/services/roomService.ts`**

Add these methods to the existing `roomService` export (preserve current methods):

```typescript
import type { ChatRoom } from '../types/room';
import type { RoomBan, RoomMemberView } from '../types/roomModeration';
import axios from 'axios';

// ... existing methods stay ...

// New methods:

listMyRooms: async (): Promise<ChatRoom[]> => {
  return (await axios.get('/api/rooms/mine')).data;
},

deleteRoom: async (roomId: string): Promise<void> => {
  await axios.delete(`/api/rooms/${roomId}`);
},

listMembersWithRole: async (roomId: string): Promise<RoomMemberView[]> => {
  return (await axios.get(`/api/rooms/${roomId}/members`)).data;
},

kickMember: async (roomId: string, userId: string): Promise<void> => {
  await axios.delete(`/api/rooms/${roomId}/members/${userId}`);
},

promoteAdmin: async (roomId: string, userId: string): Promise<void> => {
  await axios.post(`/api/rooms/${roomId}/admins`, { userId });
},

demoteAdmin: async (roomId: string, userId: string): Promise<void> => {
  await axios.delete(`/api/rooms/${roomId}/admins/${userId}`);
},

listBans: async (roomId: string): Promise<RoomBan[]> => {
  return (await axios.get(`/api/rooms/${roomId}/bans`)).data;
},

unbanMember: async (roomId: string, userId: string): Promise<void> => {
  await axios.delete(`/api/rooms/${roomId}/bans/${userId}`);
},

createRoom: async (name: string, description?: string, visibility?: 'public' | 'private'): Promise<ChatRoom> => {
  const body: Record<string, unknown> = { name };
  if (description !== undefined) body.description = description;
  if (visibility !== undefined) body.visibility = visibility;
  return (await axios.post('/api/rooms', body)).data;
},
```

Replace the existing `createRoom` with the three-argument signature above (the new param is optional so existing callers continue to work).

- [ ] **Step 4: Create `frontend/src/services/roomInvitationService.ts`**

```typescript
import axios from 'axios';
import type { RoomInvitation } from '../types/roomModeration';

export const roomInvitationService = {
  invite: async (roomId: string, username: string): Promise<void> => {
    await axios.post(`/api/rooms/${roomId}/invitations`, { username });
  },

  cancelInvitation: async (roomId: string, invitationId: string): Promise<void> => {
    await axios.delete(`/api/rooms/${roomId}/invitations/${invitationId}`);
  },

  listOutgoingForRoom: async (roomId: string): Promise<RoomInvitation[]> => {
    return (await axios.get(`/api/rooms/${roomId}/invitations`)).data;
  },

  listMyIncoming: async (): Promise<RoomInvitation[]> => {
    return (await axios.get('/api/invitations')).data;
  },

  acceptInvitation: async (invitationId: string): Promise<void> => {
    await axios.post(`/api/invitations/${invitationId}/accept`);
  },

  declineInvitation: async (invitationId: string): Promise<void> => {
    await axios.post(`/api/invitations/${invitationId}/decline`);
  },
};
```

- [ ] **Step 5: Verify frontend build**

```bash
cd /src/ai_hakaton/frontend
npm run build
```

Expected: clean build.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/types/roomModeration.ts \
        frontend/src/services/roomInvitationService.ts \
        frontend/src/services/roomService.ts
git commit -m "feat(frontend): room moderation + invitation types and services"
```

---

### Task 12: Frontend hooks

**Files:**
- Create: `frontend/src/hooks/useMyRooms.ts`
- Create: `frontend/src/hooks/useRoomInvitations.ts`
- Create: `frontend/src/hooks/useRoomMembersWithRole.ts`
- Create: `frontend/src/hooks/useRoomAdminActions.ts`

- [ ] **Step 1: useMyRooms**

```typescript
// frontend/src/hooks/useMyRooms.ts
import { useCallback, useEffect, useState } from 'react';
import type { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';

export function useMyRooms() {
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setRooms(await roomService.listMyRooms());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  return { rooms, error, reload };
}
```

- [ ] **Step 2: useRoomInvitations**

```typescript
// frontend/src/hooks/useRoomInvitations.ts
import { useCallback, useEffect, useState } from 'react';
import type { RoomInvitation } from '../types/roomModeration';
import { roomInvitationService } from '../services/roomInvitationService';

export function useRoomInvitations() {
  const [invitations, setInvitations] = useState<RoomInvitation[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setInvitations(await roomInvitationService.listMyIncoming());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const accept = useCallback(async (invitationId: string) => {
    await roomInvitationService.acceptInvitation(invitationId);
    setInvitations((prev) => prev.filter((i) => i.id !== invitationId));
  }, []);

  const decline = useCallback(async (invitationId: string) => {
    await roomInvitationService.declineInvitation(invitationId);
    setInvitations((prev) => prev.filter((i) => i.id !== invitationId));
  }, []);

  return { invitations, error, accept, decline, reload };
}
```

- [ ] **Step 3: useRoomMembersWithRole**

```typescript
// frontend/src/hooks/useRoomMembersWithRole.ts
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { RoomMemberView, RoomRole } from '../types/roomModeration';
import { roomService } from '../services/roomService';

export function useRoomMembersWithRole(roomId: string | undefined, currentUserId: string | undefined) {
  const [members, setMembers] = useState<RoomMemberView[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!roomId) return;
    try {
      setMembers(await roomService.listMembersWithRole(roomId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  useEffect(() => {
    reload();
  }, [reload]);

  const myRow = useMemo(
    () => members.find((m) => m.userId === currentUserId),
    [members, currentUserId],
  );
  const myRole: RoomRole | undefined = myRow?.role;
  const isAdmin = !!myRow && (myRow.isOwner || myRow.role === 'admin');
  const isOwner = !!myRow && myRow.isOwner;

  return { members, error, myRole, isAdmin, isOwner, reload };
}
```

- [ ] **Step 4: useRoomAdminActions**

```typescript
// frontend/src/hooks/useRoomAdminActions.ts
import { useCallback } from 'react';
import { roomService } from '../services/roomService';
import { roomInvitationService } from '../services/roomInvitationService';

export function useRoomAdminActions(roomId: string | undefined) {
  const kick = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.kickMember(roomId, userId);
    },
    [roomId],
  );

  const promote = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.promoteAdmin(roomId, userId);
    },
    [roomId],
  );

  const demote = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.demoteAdmin(roomId, userId);
    },
    [roomId],
  );

  const unban = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.unbanMember(roomId, userId);
    },
    [roomId],
  );

  const invite = useCallback(
    async (username: string) => {
      if (!roomId) return;
      await roomInvitationService.invite(roomId, username);
    },
    [roomId],
  );

  const deleteRoom = useCallback(async () => {
    if (!roomId) return;
    await roomService.deleteRoom(roomId);
  }, [roomId]);

  return { kick, promote, demote, unban, invite, deleteRoom };
}
```

- [ ] **Step 5: Verify build**

```bash
cd /src/ai_hakaton/frontend
npm run build
```

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/hooks/useMyRooms.ts \
        frontend/src/hooks/useRoomInvitations.ts \
        frontend/src/hooks/useRoomMembersWithRole.ts \
        frontend/src/hooks/useRoomAdminActions.ts
git commit -m "feat(frontend): room moderation + invitation hooks"
```

---

## Section F: Frontend — Components & Pages

### Task 13: RoomCreateModal visibility picker

**Files:**
- Modify: `frontend/src/components/RoomCreateModal.tsx`

- [ ] **Step 1: Replace the modal with visibility-aware version**

Replace with:

```tsx
import React, { useState } from 'react';

interface RoomCreateModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (name: string, description?: string, visibility?: 'public' | 'private') => Promise<void>;
}

export const RoomCreateModal: React.FC<RoomCreateModalProps> = ({ isOpen, onClose, onCreate }) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [visibility, setVisibility] = useState<'public' | 'private'>('public');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Room name is required');
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      await onCreate(name, description || undefined, visibility);
      setName('');
      setDescription('');
      setVisibility('public');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create room');
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96">
        <h2 className="text-xl font-bold mb-4">Create Room</h2>
        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label className="block text-sm font-medium mb-2">Room Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full border rounded px-3 py-2"
              placeholder="Enter room name"
              disabled={isLoading}
            />
          </div>

          <div className="mb-4">
            <label className="block text-sm font-medium mb-2">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full border rounded px-3 py-2"
              placeholder="Enter description"
              disabled={isLoading}
              rows={3}
            />
          </div>

          <div className="mb-4">
            <span className="block text-sm font-medium mb-2">Visibility</span>
            <label className="inline-flex items-center mr-4">
              <input
                type="radio"
                name="visibility"
                value="public"
                checked={visibility === 'public'}
                onChange={() => setVisibility('public')}
                disabled={isLoading}
                className="mr-2"
              />
              Public
            </label>
            <label className="inline-flex items-center">
              <input
                type="radio"
                name="visibility"
                value="private"
                checked={visibility === 'private'}
                onChange={() => setVisibility('private')}
                disabled={isLoading}
                className="mr-2"
              />
              Private (invitation only)
            </label>
          </div>

          {error && <div className="text-red-500 text-sm mb-4">{error}</div>}
          <div className="flex gap-2 justify-end">
            <button type="button" onClick={onClose} disabled={isLoading} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded">
              Cancel
            </button>
            <button type="submit" disabled={isLoading} className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400">
              {isLoading ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Update RoomListPage callback shape**

Find `handleCreateRoom` in `RoomListPage.tsx`. Change from `(name, description)` → `(name, description, visibility)` and forward to `roomService.createRoom(name, description, visibility)`.

- [ ] **Step 3: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/RoomCreateModal.tsx frontend/src/pages/RoomListPage.tsx
git commit -m "feat(frontend): RoomCreateModal visibility picker"
```

---

### Task 14: RoomListPage tabbed layout

**Files:**
- Create: `frontend/src/components/RoomInvitationList.tsx`
- Modify: `frontend/src/pages/RoomListPage.tsx`

- [ ] **Step 1: Create RoomInvitationList**

```tsx
// frontend/src/components/RoomInvitationList.tsx
import React from 'react';
import type { RoomInvitation } from '../types/roomModeration';

interface Props {
  invitations: RoomInvitation[];
  onAccept: (id: string) => Promise<void> | void;
  onDecline: (id: string) => Promise<void> | void;
}

export const RoomInvitationList: React.FC<Props> = ({ invitations, onAccept, onDecline }) => {
  if (invitations.length === 0) return null;
  return (
    <section className="mb-6 border rounded bg-yellow-50 p-4">
      <h2 className="font-semibold mb-2">Pending invitations</h2>
      <ul className="space-y-2">
        {invitations.map((inv) => (
          <li key={inv.id} className="flex justify-between items-center bg-white rounded px-3 py-2 border">
            <span>
              <strong>{inv.inviterUsername}</strong> invited you to <strong>{inv.roomName}</strong>
            </span>
            <div className="space-x-2">
              <button
                onClick={() => onAccept(inv.id)}
                className="px-3 py-1 bg-green-500 text-white rounded hover:bg-green-600"
              >
                Accept
              </button>
              <button
                onClick={() => onDecline(inv.id)}
                className="px-3 py-1 border rounded hover:bg-gray-100"
              >
                Decline
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
};
```

- [ ] **Step 2: Update RoomListPage with tabs**

Replace `frontend/src/pages/RoomListPage.tsx` with:

```tsx
import React, { useState, useEffect } from 'react';
import { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { RoomInvitationList } from '../components/RoomInvitationList';
import { useMyRooms } from '../hooks/useMyRooms';
import { useRoomInvitations } from '../hooks/useRoomInvitations';
import { useNavigate } from 'react-router-dom';

type Tab = 'public' | 'mine';

export const RoomListPage: React.FC = () => {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('public');
  const [publicRooms, setPublicRooms] = useState<ChatRoom[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const { rooms: myRooms, reload: reloadMyRooms } = useMyRooms();
  const { invitations, accept, decline } = useRoomInvitations();

  useEffect(() => {
    (async () => {
      try {
        const result = await roomService.listPublicRooms(0, 20);
        setPublicRooms(result.content);
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      }
    })();
  }, []);

  const handleCreateRoom = async (
    name: string,
    description?: string,
    visibility?: 'public' | 'private',
  ) => {
    const newRoom = await roomService.createRoom(name, description, visibility);
    await reloadMyRooms();
    navigate(`/rooms/${newRoom.id}`);
  };

  const handleAccept = async (id: string) => {
    await accept(id);
    await reloadMyRooms();
  };

  const renderRoomCard = (room: ChatRoom) => (
    <div
      key={room.id}
      className="bg-white rounded-lg shadow p-4 cursor-pointer hover:shadow-lg"
      onClick={() => navigate(`/rooms/${room.id}`)}
    >
      <div className="flex justify-between items-start mb-2">
        <h2 className="text-lg font-bold">{room.name}</h2>
        <span
          className={`text-xs px-2 py-1 rounded ${
            room.visibility === 'private' ? 'bg-purple-100 text-purple-700' : 'bg-green-100 text-green-700'
          }`}
        >
          {room.visibility}
        </span>
      </div>
      {room.description && <p className="text-gray-600 text-sm mb-4">{room.description}</p>}
      <div className="text-xs text-gray-400">Created {new Date(room.createdAt).toLocaleDateString()}</div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-100 p-6">
      <div className="max-w-6xl mx-auto">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-3xl font-bold">Chat Rooms</h1>
          <button onClick={() => setIsModalOpen(true)} className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600">
            New Room
          </button>
        </div>

        <div className="flex gap-6 border-b mb-6">
          <button
            onClick={() => setTab('public')}
            className={`pb-2 ${tab === 'public' ? 'border-b-2 border-blue-500 font-semibold' : 'text-gray-500'}`}
          >
            Public rooms
          </button>
          <button
            onClick={() => setTab('mine')}
            className={`pb-2 ${tab === 'mine' ? 'border-b-2 border-blue-500 font-semibold' : 'text-gray-500'}`}
          >
            My rooms {myRooms.length > 0 && <span className="ml-1 text-xs text-gray-400">({myRooms.length})</span>}
          </button>
        </div>

        {error && <div className="text-red-500 mb-4">{error}</div>}

        {tab === 'public' && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {publicRooms.map(renderRoomCard)}
          </div>
        )}

        {tab === 'mine' && (
          <>
            <RoomInvitationList invitations={invitations} onAccept={handleAccept} onDecline={decline} />
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {myRooms.map(renderRoomCard)}
            </div>
            {myRooms.length === 0 && invitations.length === 0 && (
              <p className="text-gray-500 italic">You haven't joined any rooms yet.</p>
            )}
          </>
        )}

        <RoomCreateModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onCreate={handleCreateRoom} />
      </div>
    </div>
  );
};
```

- [ ] **Step 3: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/RoomInvitationList.tsx frontend/src/pages/RoomListPage.tsx
git commit -m "feat(frontend): tabbed RoomListPage with Public | My rooms + invitation cards"
```

---

### Task 15: InviteUserModal

**Files:**
- Create: `frontend/src/components/InviteUserModal.tsx`

- [ ] **Step 1: Create InviteUserModal**

```tsx
import React, { useState } from 'react';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onInvite: (username: string) => Promise<void>;
}

export const InviteUserModal: React.FC<Props> = ({ isOpen, onClose, onInvite }) => {
  const [username, setUsername] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await onInvite(username.trim());
      setUsername('');
      onClose();
    } catch (err) {
      const anyErr = err as { response?: { data?: { message?: string } }; message?: string };
      setError(anyErr?.response?.data?.message || anyErr?.message || 'Invite failed');
    } finally {
      setBusy(false);
    }
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96">
        <h2 className="text-xl font-bold mb-4">Invite user to this room</h2>
        <form onSubmit={submit}>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Username"
            disabled={busy}
            className="w-full border rounded px-3 py-2 mb-4"
          />
          {error && <div className="text-red-500 text-sm mb-4">{error}</div>}
          <div className="flex gap-2 justify-end">
            <button type="button" onClick={onClose} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded">Cancel</button>
            <button type="submit" disabled={busy || !username.trim()} className="px-4 py-2 bg-blue-500 text-white rounded disabled:bg-gray-400">
              {busy ? 'Inviting...' : 'Send invitation'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/InviteUserModal.tsx
git commit -m "feat(frontend): InviteUserModal component"
```

---

### Task 16: RoomMembersPanel admin controls

**Files:**
- Modify: `frontend/src/components/RoomMembersPanel.tsx`

- [ ] **Step 1: Replace RoomMembersPanel**

```tsx
import React, { useEffect, useState, useCallback } from 'react';
import { roomService } from '../services/roomService';
import { friendshipService } from '../services/friendshipService';
import type { RoomMemberView } from '../types/roomModeration';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';
import { InviteUserModal } from './InviteUserModal';

interface Props {
  roomId: string;
  currentUserId: string;
  roomVisibility?: 'public' | 'private';
  onOpenBans?: () => void;
}

export const RoomMembersPanel: React.FC<Props> = ({ roomId, currentUserId, roomVisibility, onOpenBans }) => {
  const { members, isAdmin, reload } = useRoomMembersWithRole(roomId, currentUserId);
  const [friendIds, setFriendIds] = useState<Set<string>>(new Set());
  const [isInviteOpen, setInviteOpen] = useState(false);
  const { kick, promote, demote, invite } = useRoomAdminActions(roomId);

  useEffect(() => {
    friendshipService.listFriends().then((fs) => setFriendIds(new Set(fs.map((f) => f.userId))));
  }, []);

  const sendRequest = useCallback(async (username: string, userId: string) => {
    try {
      await friendshipService.sendRequest(username);
      setFriendIds((prev) => new Set([...prev, userId]));
    } catch (e) {
      console.error('Friend request failed', e);
    }
  }, []);

  const doKick = async (userId: string) => {
    await kick(userId);
    await reload();
  };

  const doPromote = async (userId: string) => {
    await promote(userId);
    await reload();
  };

  const doDemote = async (userId: string) => {
    await demote(userId);
    await reload();
  };

  const doInvite = async (username: string) => {
    await invite(username);
  };

  const roleBadge = (m: RoomMemberView) => {
    if (m.isOwner) return <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded">owner</span>;
    if (m.role === 'admin') return <span className="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded">admin</span>;
    return null;
  };

  return (
    <aside className="w-64 border-l bg-white p-4 overflow-y-auto">
      <div className="flex justify-between items-center mb-2">
        <h3 className="font-semibold">Members</h3>
        {isAdmin && (
          <div className="flex gap-1">
            {roomVisibility === 'private' && (
              <button onClick={() => setInviteOpen(true)} className="text-xs px-2 py-1 border rounded hover:bg-blue-50">Invite</button>
            )}
            {onOpenBans && (
              <button onClick={onOpenBans} className="text-xs px-2 py-1 border rounded hover:bg-blue-50">Bans</button>
            )}
          </div>
        )}
      </div>

      <ul className="space-y-2">
        {members.map((m) => {
          const isMe = m.userId === currentUserId;
          const isFriend = friendIds.has(m.userId);
          return (
            <li key={m.userId} className="flex flex-col text-sm">
              <div className="flex justify-between items-center">
                <span className="flex items-center gap-1">
                  {m.username}
                  {isMe && ' (you)'}
                  {roleBadge(m)}
                </span>
                {!isMe && !isFriend && (
                  <button onClick={() => sendRequest(m.username, m.userId)} className="text-xs px-2 py-1 border rounded hover:bg-blue-50">
                    Add friend
                  </button>
                )}
              </div>
              {isAdmin && !isMe && !m.isOwner && (
                <div className="flex gap-1 mt-1 text-xs">
                  <button onClick={() => doKick(m.userId)} className="px-2 py-0.5 border border-red-400 text-red-600 rounded hover:bg-red-50">
                    Kick
                  </button>
                  {m.role === 'admin' ? (
                    <button onClick={() => doDemote(m.userId)} className="px-2 py-0.5 border rounded hover:bg-gray-100">
                      Demote
                    </button>
                  ) : (
                    <button onClick={() => doPromote(m.userId)} className="px-2 py-0.5 border rounded hover:bg-gray-100">
                      Promote
                    </button>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>

      <InviteUserModal isOpen={isInviteOpen} onClose={() => setInviteOpen(false)} onInvite={doInvite} />
    </aside>
  );
};
```

- [ ] **Step 2: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/RoomMembersPanel.tsx
git commit -m "feat(frontend): admin controls + role badges on RoomMembersPanel"
```

---

### Task 17: BanListPanel

**Files:**
- Create: `frontend/src/components/BanListPanel.tsx`

- [ ] **Step 1: Create BanListPanel**

```tsx
import React, { useCallback, useEffect, useState } from 'react';
import { roomService } from '../services/roomService';
import type { RoomBan } from '../types/roomModeration';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
}

export const BanListPanel: React.FC<Props> = ({ isOpen, onClose, roomId }) => {
  const [bans, setBans] = useState<RoomBan[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setBans(await roomService.listBans(roomId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  useEffect(() => {
    if (isOpen) reload();
  }, [isOpen, reload]);

  const unban = async (userId: string) => {
    await roomService.unbanMember(roomId, userId);
    setBans((prev) => prev.filter((b) => b.bannedUserId !== userId));
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-[28rem]">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold">Banned users</h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700">×</button>
        </div>
        {error && <div className="text-red-500 text-sm mb-4">{error}</div>}
        {bans.length === 0 ? (
          <p className="text-gray-500 italic">No banned users.</p>
        ) : (
          <ul className="divide-y">
            {bans.map((b) => (
              <li key={b.bannedUserId} className="flex justify-between items-center py-2">
                <div>
                  <div className="font-medium">{b.bannedUsername}</div>
                  <div className="text-xs text-gray-500">
                    banned by {b.bannedByUsername} on {new Date(b.bannedAt).toLocaleString()}
                  </div>
                </div>
                <button onClick={() => unban(b.bannedUserId)} className="px-3 py-1 border rounded hover:bg-gray-100">
                  Unban
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/BanListPanel.tsx
git commit -m "feat(frontend): BanListPanel (admin modal with unban action)"
```

---

### Task 18: DeleteRoomDialog + ChatPage integration

**Files:**
- Create: `frontend/src/components/DeleteRoomDialog.tsx`
- Modify: `frontend/src/pages/ChatPage.tsx`

- [ ] **Step 1: Create DeleteRoomDialog**

```tsx
import React, { useState } from 'react';

interface Props {
  isOpen: boolean;
  roomName: string;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}

export const DeleteRoomDialog: React.FC<Props> = ({ isOpen, roomName, onConfirm, onClose }) => {
  const [busy, setBusy] = useState(false);

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96">
        <h2 className="text-xl font-bold mb-2">Delete room?</h2>
        <p className="text-sm text-gray-700 mb-4">
          This will permanently delete <strong>{roomName}</strong> and all of its messages.
          This cannot be undone.
        </p>
        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded">
            Cancel
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm();
              } finally {
                setBusy(false);
              }
            }}
            className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 disabled:bg-gray-400"
          >
            {busy ? 'Deleting...' : 'Delete room'}
          </button>
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Wire up ChatPage**

In `frontend/src/pages/ChatPage.tsx`, import the new components and hook, track bans-panel and delete-dialog state, show Delete-Room button for owner, and display access-denied screen if fetchRoom fails or current user isn't a member.

Add these imports near the top (keep existing):

```typescript
import { BanListPanel } from '../components/BanListPanel';
import { DeleteRoomDialog } from '../components/DeleteRoomDialog';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';
```

Inside the ChatPage component (after existing hooks), add:

```typescript
const { isOwner } = useRoomMembersWithRole(roomId, currentUserId ?? undefined);
const { deleteRoom } = useRoomAdminActions(roomId);
const [bansOpen, setBansOpen] = useState(false);
const [deleteOpen, setDeleteOpen] = useState(false);

const handleDeleteRoom = async () => {
  await deleteRoom();
  navigate('/rooms');
};
```

Update the header JSX so the buttons look like:

```tsx
<div className="max-w-6xl mx-auto flex justify-between items-center">
  <div>
    <h1 className="text-2xl font-bold">{currentRoom?.name || 'Loading...'}</h1>
    {currentRoom?.description && <p className="text-gray-600 text-sm">{currentRoom.description}</p>}
  </div>
  <div className="flex gap-2">
    {isOwner && (
      <button
        onClick={() => setDeleteOpen(true)}
        className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
      >
        Delete Room
      </button>
    )}
    <button onClick={handleLeaveRoom} className="px-4 py-2 border rounded hover:bg-gray-100">
      Leave Room
    </button>
  </div>
</div>
```

Pass the new props to `RoomMembersPanel`:

```tsx
{roomId && currentUserId && (
  <RoomMembersPanel
    roomId={roomId}
    currentUserId={currentUserId}
    roomVisibility={currentRoom?.visibility as 'public' | 'private' | undefined}
    onOpenBans={() => setBansOpen(true)}
  />
)}
```

Render the modals near the end of the returned JSX:

```tsx
{roomId && <BanListPanel isOpen={bansOpen} onClose={() => setBansOpen(false)} roomId={roomId} />}
{roomId && currentRoom && (
  <DeleteRoomDialog
    isOpen={deleteOpen}
    roomName={currentRoom.name}
    onConfirm={handleDeleteRoom}
    onClose={() => setDeleteOpen(false)}
  />
)}
```

- [ ] **Step 3: Build + commit**

```bash
cd /src/ai_hakaton/frontend && npm run build
cd /src/ai_hakaton
git add frontend/src/components/DeleteRoomDialog.tsx frontend/src/pages/ChatPage.tsx
git commit -m "feat(frontend): DeleteRoomDialog + owner controls + BanListPanel wired to ChatPage"
```

---

## Section G: Playwright E2E

### Task 19: Full room-moderation lifecycle

**Files:**
- Create: `frontend/e2e/room-moderation.spec.ts`

- [ ] **Step 1: Write the test**

Create `frontend/e2e/room-moderation.spec.ts`:

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

test.describe('Room moderation', () => {
  test('create private → invite → accept → kick → unban → delete lifecycle', async ({ browser }) => {
    const owner = uniqueUser('owner');
    const bob = uniqueUser('bob');

    // Both register and log in
    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);
    await bobSession.ctx.close();

    const { ctx: ownerCtx, page: ownerPage } = await registerAndLogin(
      browser,
      owner.email,
      owner.username,
      owner.password,
    );

    // Owner creates a private room
    const roomName = `priv-${Date.now().toString().slice(-7)}`;
    await ownerPage.click('button:has-text("New Room")');
    await ownerPage.fill('input[placeholder="Enter room name"]', roomName);
    await ownerPage.check('input[value="private"]');
    await ownerPage.click('button:has-text("Create"):not(:has-text("Cancel"))');
    await ownerPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    // Owner invites Bob
    await ownerPage.click('button:has-text("Invite")');
    await ownerPage.fill('input[placeholder="Username"]', bob.username);
    await ownerPage.click('button:has-text("Send invitation")');

    await ownerCtx.close();

    // Bob logs back in and sees the invitation
    const bobCtx = await browser.newContext();
    const bobPage = await bobCtx.newPage();
    await bobPage.goto('/login');
    await bobPage.fill('#email', bob.email);
    await bobPage.fill('#password', bob.password);
    await bobPage.click('button[type="submit"]');
    await bobPage.waitForURL(/.*\/rooms$/);

    await bobPage.click('button:has-text("My rooms")');
    await expect(bobPage.locator('body')).toContainText(roomName, { timeout: 5_000 });
    await bobPage.click('button:has-text("Accept")');
    await expect(bobPage.locator('body')).toContainText(roomName);

    // Bob enters the room
    await bobPage.click(`text=${roomName}`);
    await bobPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await expect(bobPage.getByPlaceholder(/type a message/i)).toBeVisible();

    await bobCtx.close();

    // Owner kicks Bob
    const ownerCtx2 = await browser.newContext();
    const ownerPage2 = await ownerCtx2.newPage();
    await ownerPage2.goto('/login');
    await ownerPage2.fill('#email', owner.email);
    await ownerPage2.fill('#password', owner.password);
    await ownerPage2.click('button[type="submit"]');
    await ownerPage2.waitForURL(/.*\/rooms$/);
    await ownerPage2.click('button:has-text("My rooms")');
    await ownerPage2.click(`text=${roomName}`);
    await ownerPage2.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    // Click Kick on Bob's row
    const bobRow = ownerPage2.locator('li').filter({ hasText: bob.username }).first();
    await bobRow.locator('button:has-text("Kick")').click();

    // Open bans, confirm Bob is there, unban
    await ownerPage2.click('button:has-text("Bans")');
    await expect(ownerPage2.locator('body')).toContainText(bob.username, { timeout: 5_000 });
    await ownerPage2.click('button:has-text("Unban")');

    // Close bans panel
    await ownerPage2.click('button:has-text("×")');

    // Delete the room
    await ownerPage2.click('button:has-text("Delete Room")');
    await ownerPage2.click('div.fixed button:has-text("Delete room"):not(:has-text("?"))');
    await ownerPage2.waitForURL(/.*\/rooms$/, { timeout: 5_000 });

    await ownerCtx2.close();
  });
});
```

- [ ] **Step 2: Run the full test suite**

```bash
cd /src/ai_hakaton/frontend
npm run build
npm test -- --run
npm run test:e2e
```

Expected: all green, including the new room-moderation test.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add frontend/e2e/room-moderation.spec.ts
git commit -m "test(e2e): Playwright — full room moderation lifecycle

Create private room → invite user → accept → open room → kick →
verify ban list → unban → delete room."
```

---

## Verification Checklist

Before considering Feature #4 complete:

- [ ] Backend: `./gradlew test` passes (existing + ~25 new tests across services/controllers + 3 integration scenarios)
- [ ] Frontend: `npm test -- --run` passes (vitest)
- [ ] Frontend: `npm run build` clean
- [ ] Playwright: `npm run test:e2e` — full suite green including the new room-moderation test
- [ ] `docker compose up --build -d` → stack healthy; Flyway applies V4; REST smoke:
  - `POST /api/rooms body {name,visibility:"private"}` → private room created
  - `POST /api/rooms/{id}/invitations body {username}` → invitation row
  - `GET /api/invitations` as invitee → shows the pending invitation
  - `POST /api/invitations/{id}/accept` → membership added, invitation gone
  - `DELETE /api/rooms/{id}/members/{u}` → kick; `GET /api/rooms/{id}/bans` shows it
  - `DELETE /api/rooms/{id}/bans/{u}` → unban
  - `DELETE /api/rooms/{id}` as owner → 204; room gone from `/api/rooms/mine`
- [ ] Browser: visibility picker works; invitation card shows in My rooms tab; kick + unban work; delete room navigates back to /rooms
- [ ] `FEATURES_ROADMAP.md` updated — Feature #4 marked ✅ complete (per the roadmap-updates feedback rule)

---

## Notes

- Ownership transfer is NOT in scope — an owner who wants to stop owning must delete the room
- Moderation events (kick / delete-room) are discovered by other clients on their next REST call — real-time WebSocket notifications are future work
- Room ban and user-to-user ban are fully independent; the integration test asserts no cross-contamination
- Admin-deletes-individual-messages is deferred to Feature #5 so it can ship alongside user-edits-own / reply / delete
