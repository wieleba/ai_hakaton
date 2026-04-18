# Feature #4: Private Rooms, Room Moderation & Invitations — Design

**Status:** Approved
**Date:** 2026-04-18
**Deadline:** 2026-04-20 12:00 UTC

## Goal

Complete the rooms story from requirements §2.4. Feature #2 shipped public rooms with basic join/leave; this feature adds the three capabilities needed to make rooms a full product:

1. **Private rooms** — the `visibility` column exists; make it actionable. Private rooms are invisible in the public catalog and joinable only by invitation.
2. **Admin moderation** — members of a room may have role `admin`. Admins can kick members (which bans them), manage the ban list (unban), and promote/demote other admins. Owner is implicitly admin, cannot lose the role, and uniquely can delete the whole room.
3. **Invitations** — members of a private room may invite other users. Invitees see pending invitations at the top of their "My rooms" tab and accept or decline.

## Scope

**In scope**
- Create public *or* private room from the UI
- Two-tab rooms page: **Public rooms** (existing catalog) and **My rooms** (joined — public + private — plus pending invitations)
- Admin controls on the members panel inside a chat room: kick (bans), promote/demote admin, invite user (private rooms only), view ban list, unban
- Owner-only: delete the room (cascades messages, members, bans, invitations via FK)
- REST endpoints for all of the above
- Reject public-room join if caller is banned; reject private-room join without invitation

**Out of scope** (deferred; rationale in parentheses)
- Admin deletes individual messages (lands in Feature #5 with user-edits-own/reply/delete so all message-content mutation ships together for rooms AND DMs)
- File / image attachments (Feature #6)
- Real-time WebSocket broadcast of moderation events (e.g., "you were kicked") — REST-driven for now; can be added if time permits
- Password reset / email notifications for invitations (Feature #8)

**Independence:** Room ban and user-to-user ban (Feature #3) are **independent**. Kicking a friend from a room does NOT terminate the friendship or block DMs. Conversely, user-to-user ban (§2.3.5) does NOT remove either user from shared rooms. `RoomModerationService` never touches `friendships`/`user_bans`; `UserBanService` never touches `room_bans`.

## Architecture

REST endpoints for all moderation and invitation actions; per-room topic subscriptions unchanged. Ownership identified by `chat_rooms.owner_id` (existing). Admin role stored as a `role` column on `room_members` — one source of truth per (room, user), so admin status vanishes with the membership row on leave/kick. Bans and invitations live in their own tables with `ON DELETE CASCADE` to `chat_rooms` so owner-deletes-room is a single DB statement that cleans everything up.

## Data Model

Migration: `V4__room_moderation_and_invitations.sql`

### Modified — `room_members`

```sql
ALTER TABLE room_members
  ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'member'
  CHECK (role IN ('member', 'admin'));
```

- Existing rows become `'member'`. Owner row stays `'member'` too — ownership is identified by `chat_rooms.owner_id`, not by role. Owner is implicitly admin at the service layer (`isAdmin` short-circuits on `isOwner`).

### New — `room_invitations`

```sql
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
```

- Pending = row exists. **Accept** = INSERT into `room_members` + DELETE the invitation row, atomic. **Decline** = DELETE. No `status` column; no historical audit needed.

### New — `room_bans`

```sql
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

- **Kick** = DELETE from `room_members` + INSERT into `room_bans`, atomic. The spec equates the two (§2.4.8).
- `banned_by_id` preserved so admins see who banned each user (§2.4.7).
- Unban = DELETE the row; user may re-join normally (if public) or via a fresh invitation (if private).

## Backend

### Package layout (`features/rooms/`)

```
(existing)
ChatRoom.java, ChatRoomRepository.java, ChatRoomService.java, ChatRoomController.java
RoomMember.java, RoomMemberRepository.java, RoomMemberService.java

(new)
RoomBan.java                      # JPA entity
RoomBanRepository.java
RoomInvitation.java               # JPA entity
RoomInvitationRepository.java
RoomModerationService.java        # kick, ban, unban, grant/revoke admin, delete room
RoomModerationController.java
RoomInvitationService.java
RoomInvitationController.java
```

Services split by responsibility — lifecycle (`ChatRoomService`), moderation (`RoomModerationService`), invitations (`RoomInvitationService`). Keeps each file small enough to hold in head and each concern tested independently.

### Entity change: `RoomMember`

Adds `private String role;` with constants `ROLE_MEMBER = "member"` and `ROLE_ADMIN = "admin"`. Default on creation is `member` (idiomatic Lombok `@Builder.Default` or set in `addMember`).

### Permission helpers (added to `RoomMemberService`)

```java
boolean isMember(UUID roomId, UUID userId);   // existing
boolean isAdmin(UUID roomId, UUID userId);    // isOwner OR role=admin
boolean isOwner(UUID roomId, UUID userId);    // chatRoom.ownerId == userId
```

Every service method that requires admin permission calls `isAdmin`. Methods that need ownership check (delete room) call `isOwner`.

### REST endpoints

**Rooms** (extended / new on `ChatRoomController`):

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/rooms` | any auth'd | Body `{name, description?, visibility?}`. Default `visibility="public"`. Creator auto-added to `room_members` as `member`. |
| GET | `/api/rooms` | any | Existing — list public rooms. |
| GET | `/api/rooms/mine` | any | List rooms where current user is a member (public + private). |
| GET | `/api/rooms/{id}` | member | Room details. Returns 404 for non-members when room is private (don't leak existence). |
| DELETE | `/api/rooms/{id}` | owner | Delete the room. Cascades via FK. |
| POST | `/api/rooms/{id}/join` | any | Existing. Rejects if visibility=`private` (403) or caller is in `room_bans` (403). |
| POST | `/api/rooms/{id}/leave` | member | Existing. Owner rejected with 400. |
| GET | `/api/rooms/{id}/members` | member | Each member row includes `role` and an `isOwner` flag (backend computes `userId == room.ownerId`). |

**Moderation** (`RoomModerationController`; all admin/owner-only):

| Method | Path | Description |
|---|---|---|
| DELETE | `/api/rooms/{id}/members/{userId}` | Kick. One transaction: DELETE from `room_members` + INSERT into `room_bans`. Cannot kick owner; cannot kick self. |
| POST | `/api/rooms/{id}/admins` body `{userId}` | Promote a member to admin. Target must be a member. Idempotent (no-op if already admin). |
| DELETE | `/api/rooms/{id}/admins/{userId}` | Demote admin. Cannot demote owner; cannot demote self. |
| GET | `/api/rooms/{id}/bans` | List banned users with `banned_by` + `banned_at` (admin/owner only). |
| DELETE | `/api/rooms/{id}/bans/{userId}` | Unban (admin/owner only). |

**Invitations** (`RoomInvitationController`):

| Method | Path | Who | Description |
|---|---|---|---|
| POST | `/api/rooms/{id}/invitations` body `{username}` | member of room | Invite. Rejects self-invite, already-member, banned-user, duplicate-pending. Also rejects invitation to public room (400 — no invitation needed). |
| GET | `/api/rooms/{id}/invitations` | member of room | Outgoing list for this room. |
| DELETE | `/api/rooms/{id}/invitations/{invId}` | inviter or admin | Cancel pending. |
| GET | `/api/invitations` | any auth'd | **My** pending incoming invitations across all rooms. Response includes `roomName` and `inviterUsername` so UI renders without extra lookups. |
| POST | `/api/invitations/{invId}/accept` | invitee only | Creates membership, deletes invitation row. Rechecks ban defensively. |
| POST | `/api/invitations/{invId}/decline` | invitee only | Deletes invitation row. |

### Validation invariants (service-level, not just UI)

- `createRoom`: visibility ∈ `{public, private}`; default `public`.
- `joinRoom`: 403 if `visibility=private` ("use invitation"); 403 if in `room_bans` for this room.
- `deleteRoom`: only owner.
- `kick`: admin caller; target must not be owner; caller ≠ target; ban row inserted in same transaction.
- `grantAdmin`: target must be a member; idempotent (no-op when already admin).
- `revokeAdmin`: target must currently be admin; never the owner; never the caller.
- `unban`: admin caller; row deleted if present, no-op otherwise.
- `invite`: inviter must be a member of the room; room must be private; invitee must not already be a member or banned; no duplicate pending row (enforced by UNIQUE).
- `acceptInvitation`: only the invitee; rechecks ban (race-proof).
- `cancelInvitation`: inviter or admin.

### DTO shapes

```java
// For GET /api/rooms/{id}/members
record RoomMemberView(UUID userId, String username, String role, boolean isOwner);

// For GET /api/rooms/{id}/bans
record RoomBanView(UUID bannedUserId, String bannedUsername,
                   UUID bannedById, String bannedByUsername,
                   OffsetDateTime bannedAt);

// For GET /api/invitations (my inbox)
record RoomInvitationView(UUID id, UUID roomId, String roomName,
                           UUID inviterId, String inviterUsername,
                           OffsetDateTime createdAt);
```

Backend resolves usernames in the response so the frontend doesn't do N+1 lookups.

### Tests

- **Service unit tests** (Mockito + Instancio) for each service; cover every "rejects when…" branch in the validation invariants above.
- **Controller tests** using `@SpringBootTest @MockBean` + the existing `TestSecurityConfig` pattern.
- **End-to-end integration test** (`RoomModerationFlowIntegrationTest`) walks the full lifecycle: create private room → invite → accept → promote admin → kick → unban → delete room. Asserts each DB state transition.

## Frontend

### Routes

No new top-level routes; existing `/rooms` and `/rooms/:id` get extended UI.

### `RoomListPage` — tabbed

- **Public rooms** tab: current catalog (unchanged).
- **My rooms** tab:
  - `RoomInvitationList` at the top rendering pending invitations as cards with Accept / Decline buttons.
  - Joined rooms below (served by `GET /api/rooms/mine`, includes a visibility badge).

### `RoomCreateModal` — visibility picker

Two radio buttons **Public** (default) / **Private**. Sent as `visibility` in the POST body.

### `ChatPage` / `RoomMembersPanel` — admin controls

The members panel becomes role-aware:

- Each member row shows a role badge (`owner`, `admin`, `member`).
- If current user `isAdmin`:
  - Non-owner non-self rows get a ⋯ menu: **Kick (and ban)** / **Promote to admin** / **Demote admin** (shown based on target's current role; hidden if target is owner).
  - Panel header: **View bans** button (opens `BanListPanel`), and **Invite user** (private rooms only; opens `InviteUserModal`).
- If current user `isOwner`:
  - All admin controls + a red **Delete room** button in the page header (next to Leave). Confirm dialog before DELETE.

### New components

| Component | Purpose |
|---|---|
| `RoomInvitationList` | Accept / Decline cards for pending invitations; used on My-rooms tab. |
| `InviteUserModal` | Username input + submit → `POST /api/rooms/{id}/invitations`. Error banner on rejection. |
| `BanListPanel` | Modal/drawer, admin-only, lists bans with banned-by + banned-at + Unban button. |
| `DeleteRoomDialog` | Confirmation dialog for the owner's irreversible DELETE. |

### Services

`roomService.ts` gains:

```typescript
listMyRooms()
deleteRoom(roomId)
kickMember(roomId, userId)
promoteAdmin(roomId, userId)
demoteAdmin(roomId, userId)
listBans(roomId)
unbanMember(roomId, userId)
```

New `roomInvitationService.ts`:

```typescript
invite(roomId, username)
cancelInvitation(roomId, invitationId)
listOutgoingForRoom(roomId)
listMyIncoming()
acceptInvitation(invitationId)
declineInvitation(invitationId)
```

### Hooks

| Hook | Purpose |
|---|---|
| `useMyRooms()` | Joined-rooms list + `reload()` called after invitation accept / room delete. |
| `useRoomInvitations()` | Current user's pending incoming invitations; exposes accept/decline. |
| `useRoomMembersWithRole(roomId)` | Extends current members fetch with role info + derived `myRole`, `isAdmin`, `isOwner` flags. |
| `useRoomAdminActions(roomId)` | Wraps kick/promote/demote/invite/unban/delete-room with optimistic local state updates. |

### Types

```typescript
export type RoomRole = 'member' | 'admin';

export interface RoomMemberView {
  userId: string;
  username: string;
  role: RoomRole;
  isOwner: boolean;
}

export interface RoomInvitation {
  id: string;
  roomId: string;
  roomName: string;
  inviterId: string;
  inviterUsername: string;
  createdAt: string;
}

export interface RoomBan {
  bannedUserId: string;
  bannedUsername: string;
  bannedById: string;
  bannedByUsername: string;
  bannedAt: string;
}
```

The existing `ChatRoom` type already includes `visibility` — no change needed.

## Key Flows

### Create private room → invite → accept
1. Creator picks **Private** in `RoomCreateModal` and submits.
2. `POST /api/rooms` with `visibility="private"` creates the room and the owner-as-member row.
3. Creator opens the room, clicks **Invite user**, enters target's username.
4. `POST /api/rooms/{id}/invitations` validates: caller is a member, room is private, target isn't a member, target isn't in `room_bans`, no duplicate pending row. INSERTs `room_invitations`.
5. Invitee's **My rooms** tab now shows the invitation card (served by `GET /api/invitations`).
6. Invitee clicks **Accept** → `POST /api/invitations/{id}/accept`:
   - Verifies caller is the invitee.
   - Re-checks ban defensively.
   - Atomic: INSERT `room_members` + DELETE `room_invitations`.

### Admin kicks a member
1. Admin clicks ⋯ → **Kick** on member row.
2. `DELETE /api/rooms/{id}/members/{userId}`:
   - Caller is admin (owner short-circuits); target is not owner; caller ≠ target.
   - Atomic: DELETE from `room_members` + INSERT into `room_bans` with `banned_by_id = caller`.
3. Kicked user's next call to that room's endpoints fails at the membership check. `GET /api/rooms/mine` no longer lists the room. (Real-time boot is deferred.)

### Unban
1. Admin opens **View bans** (`BanListPanel`), clicks **Unban** on the target.
2. `DELETE /api/rooms/{id}/bans/{userId}` removes the ban row.
3. Previously banned user may now join public rooms or accept fresh invitations.

### Promote / demote admin
- **Promote**: `POST /api/rooms/{id}/admins body {userId}` — any admin (including owner); target must be a member; UPDATE `role='admin'`; idempotent.
- **Demote**: `DELETE /api/rooms/{id}/admins/{userId}` — any admin; target must currently be admin; not the owner; not the caller; UPDATE `role='member'`.

### Owner deletes the room
1. Owner clicks **Delete room**, confirms.
2. `DELETE /api/rooms/{id}`:
   - Verifies caller is owner.
   - DELETE FROM `chat_rooms` cascades to messages, room_members, room_bans, room_invitations.
3. UI navigates to `/rooms`; other clients discover the deletion on next interaction.

### Join attempts
- Private room with no invitation → 403 `{error: "Cannot join private room"}`.
- Banned from a public room → 403 `{error: "Cannot join this room"}` (doesn't leak the specific reason, consistent with §2.3.5's silent ban).

### Error / edge cases
- Cancel a pending invitation (inviter or admin) → 204.
- Self-invite blocked at DB CHECK + service.
- Re-invite after decline is allowed — decline deletes the row, so no UNIQUE collision.
- Invite to public room → 400 (no invitation needed; keep UX unambiguous).

## Independence From Feature #3

Scope separation is explicit and enforced by the service boundary:

- `RoomModerationService` never reads or writes `friendships` / `user_bans`.
- `UserBanService` never reads or writes `room_bans`.
- Integration tests assert: user-to-user ban does not remove the banned user from shared rooms; room kick does not terminate friendship.

## Non-Goals / Notes

- Ownership transfer (e.g., "make someone else the owner") is out of scope. If the owner wants to leave, they must delete the room.
- No UI for a user to see a list of rooms they've been banned from. Admins can see the ban list per room.
- Invitations are single-use — no "generate a link anyone can use" model.
- No WebSocket events for moderation actions in this feature; clients discover state changes on their next REST call. Acceptable for hackathon scope.
