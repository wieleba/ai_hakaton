# Feature #8: Account Management — Design

**Status:** approved 2026-04-19
**Scope:** password change for logged-in users + account deletion with cascade.
**Out of scope:** password reset (split to Feature #9 — requires SMTP infrastructure).

## Goal

1. Logged-in users can change their password by providing old + new password.
2. Logged-in users can delete their own account. Deletion removes:
   - Their `users` row
   - Every `chat_room` they own (and cascading: that room's messages, members, invitations, bans)
   - Every room membership in rooms owned by other users
   - Every friendship they're part of
   - Every DM conversation they participate in (and its messages)
   - Every user-ban they issued or received
   - Every reaction they made
   - Every invitation they sent or received
   - Every `deleted_by` credit on messages (nulled, not deleted)
   - Their messages in rooms they did **not** own stay with `user_id = NULL` so other users' chat history is preserved — frontend/service renders "Deleted user" for the author.

## Non-goals

| Out of scope | Why |
|---|---|
| Password reset flow | Full SMTP/email subsystem — Feature #9 |
| Admin-driven deletion | YAGNI for hackathon |
| Data export before deletion | YAGNI |
| Undo window | YAGNI; "permanent" matches the requirement |
| Ghost-user sentinel for past messages | `user_id NULL` + display fallback is simpler than inserting a sentinel row |

## Schema — V7 migration

```sql
-- Step 1: allow messages.user_id to be NULL so "author deleted" is representable
ALTER TABLE messages ALTER COLUMN user_id DROP NOT NULL;

-- Step 2: rewrite every user-referencing FK with the right cascade semantics.
-- PostgreSQL doesn't support "ALTER CONSTRAINT ... ON DELETE" for FKs directly,
-- so DROP + ADD is the pattern.

-- chat_rooms.owner_id — CASCADE (delete owner → delete room)
ALTER TABLE chat_rooms DROP CONSTRAINT fk_chat_rooms_owner;
ALTER TABLE chat_rooms
  ADD CONSTRAINT fk_chat_rooms_owner
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

-- room_members.user_id — CASCADE
ALTER TABLE room_members DROP CONSTRAINT fk_room_members_user;
ALTER TABLE room_members
  ADD CONSTRAINT fk_room_members_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- messages.user_id — SET NULL (keep the message, null the author)
ALTER TABLE messages DROP CONSTRAINT fk_messages_user;
ALTER TABLE messages
  ADD CONSTRAINT fk_messages_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- friendships (requester_id, addressee_id) — CASCADE on both sides
-- V3 uses an inline FK on CREATE TABLE; find the generated constraint names
-- via information_schema and drop each, then re-add with CASCADE. The plan
-- step runs this inside a DO block or via explicit names — see plan.

-- user_bans (banner_id, banned_id) — CASCADE on both sides
-- direct_conversations (user1_id, user2_id) — CASCADE on both sides
-- direct_messages (sender_id) — CASCADE
-- direct_messages (deleted_by) — SET NULL (already nullable from V5)
-- room_invitations (inviter_id, invitee_id) — CASCADE on both sides
-- room_bans (banned_user_id, banned_by_id) — CASCADE on both sides
-- message_reactions.user_id — already CASCADE from V6
-- direct_message_reactions.user_id — already CASCADE from V6
-- messages.deleted_by — SET NULL (already nullable from V5)
```

The V7 migration contains the explicit `ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT` block for every FK listed above. Constraint names come from existing `information_schema.table_constraints` — the plan step uses the actual names seen in `V3`/`V4`. Inline-FK constraints (like the ones on records created inside `CREATE TABLE ... user_id UUID NOT NULL REFERENCES users(id)`) get auto-generated names such as `<table>_<col>_fkey`; the migration uses those names explicitly. If any constraint name doesn't exist as expected, the migration fails fast — good, because we'd want to investigate rather than silently skip.

Tables to touch (with constraint names we will resolve at plan time):
- `chat_rooms.fk_chat_rooms_owner` (named explicitly in V3)
- `room_members.fk_room_members_user` (named explicitly in V3)
- `messages.fk_messages_user` (named explicitly in V3)
- `friendships_requester_id_fkey`, `friendships_addressee_id_fkey` (auto-named)
- `user_bans_banner_id_fkey`, `user_bans_banned_id_fkey`
- `direct_conversations_user1_id_fkey`, `direct_conversations_user2_id_fkey`
- `direct_messages_sender_id_fkey`
- `room_invitations_inviter_id_fkey`, `room_invitations_invitee_id_fkey`
- `room_bans_banned_user_id_fkey`, `room_bans_banned_by_id_fkey`

## REST API

### Password change

```
PATCH /api/users/me/password
Authorization: Bearer <jwt>
Body: { "oldPassword": "...", "newPassword": "..." }

200 on success (empty body — frontend already has the token)
403 on wrong oldPassword
400 on newPassword shorter than 8 characters or empty
```

### Account deletion

```
DELETE /api/users/me
Authorization: Bearer <jwt>

204 on success
```

Service executes a single `userRepository.deleteById(callerId)`; cascades handle the rest. After a successful delete the caller's JWT becomes stale immediately — see JWT section.

## JWT invalidation

`JwtAuthenticationFilter` today extracts `sub` as a UUID and sets it on the Authentication details **without** a DB existence check. Add a lookup: if `userRepository.existsById(userIdFromToken)` returns false, the filter short-circuits to 401 (via the existing entry point).

This means:
- Stale tokens after account deletion return 401 on the next request.
- No cache or blacklist needed — the DB is the source of truth.
- One extra query per authenticated request; negligible at hackathon scale (300 simultaneous users target).

## Frontend

### Profile menu extension

`ProfileMenu` already renders:

```
┌─────────────┐
│ Signed in as│
│ <username>  │
├─────────────┤
│ Sign out    │
└─────────────┘
```

Extend to:

```
┌─────────────────┐
│ Signed in as    │
│ <username>      │
├─────────────────┤
│ Change password │
│ Delete account  │
├─────────────────┤
│ Sign out        │
└─────────────────┘
```

Each item opens a modal (no new route needed).

### `ChangePasswordModal`

- Fields: old password, new password, confirm new password
- Client-side checks: new ≥ 8 chars; confirm matches new
- Submit → `PATCH /api/users/me/password` → success toast + close; 403 → inline "Old password is incorrect"; 400 → inline "New password must be at least 8 characters"

### `DeleteAccountModal`

- Warning copy explicitly listing what gets deleted
- "Type `DELETE` to confirm" input — submit disabled until exact match
- Submit → `DELETE /api/users/me` → clear `localStorage.authToken` → navigate to `/login` with a one-liner toast ("Account deleted.")

## Components

### New
- `backend/src/main/java/com/hackathon/features/users/UserController` — add two endpoints (PATCH password, DELETE me). Same file, no new controller.
- `backend/src/main/java/com/hackathon/features/users/UserService` — add `changePassword(userId, oldPw, newPw)` + `deleteAccount(userId)` methods.
- `frontend/src/services/accountService.ts` — wraps `PATCH /api/users/me/password` + `DELETE /api/users/me`.
- `frontend/src/components/ChangePasswordModal.tsx`
- `frontend/src/components/DeleteAccountModal.tsx`

### Modified
- `backend/src/main/resources/db/migration/V7__account_management.sql` — new file
- `backend/src/main/java/com/hackathon/shared/security/JwtAuthenticationFilter` — add existence guard
- `frontend/src/layout/ProfileMenu.tsx` — add `Change password` + `Delete account` items

## Error handling

- Password change: service throws `IllegalArgumentException` → controller returns 403 (bad old pw) or 400 (validation); differentiated by message or by typed subclasses. Plan keeps it simple: one exception type, controller inspects message to distinguish. Frontend parses the HTTP status + message.
- Account delete: any FK issue (shouldn't happen because cascades cover everything) → 500, frontend shows "Deletion failed, try again."
- JWT 401: frontend's existing axios interceptor redirects to `/login`.

## Tests

| Level | Coverage |
|---|---|
| Backend unit | `changePassword` happy path sets new BCrypt hash; wrong old password throws; new password too short throws. `deleteAccount` removes the user row; verifies cascade: owned rooms gone, messages in non-owned rooms have `user_id = NULL`, friendships gone, DMs + conversations gone, memberships gone, bans gone. |
| Backend controller (MockMvc) | 200 PATCH happy path; 403 wrong old; 400 short new; 204 DELETE; 401 for both without JWT. |
| Backend integration | `AccountDeletionFlowIntegrationTest` — two users, Alice owns rooms, Bob messages in Alice's room and in his own. Alice sends Bob DM. Alice deletes. Assert: Alice's rooms gone, Bob's messages in Alice's rooms gone (cascade), Bob's own room + his messages in it survive, Bob's message sent inside Alice's (deleted) room is gone, DM conversation between them is gone, Bob's unrelated friends + rooms intact. |
| JWT existence check | Unit test on `JwtAuthenticationFilter` or integration via `@SpringBootTest`: delete a user, reuse their token → 401 on next request. |
| Frontend unit (vitest) | `ChangePasswordModal` validates confirm-match + min-length; disables submit otherwise. `DeleteAccountModal` disables submit until "DELETE" is typed. |
| Frontend E2E (Playwright) | New `account-management.spec.ts` — register Alice, change her password, log out, log back in with new password. Register Bob, delete Bob, verify `/login` shown and his email is reusable for a fresh registration. |

## Display fallback for deleted authors

Backend:
- `MessageService.toDto(...)` uses `resolveUsername(m.getUserId())`. If `m.getUserId() == null` (post-deletion via SET NULL), the method returns the string `"Deleted user"`. Current fallback (catching `IllegalArgumentException` and returning uuid prefix) stays for missing-user-row edge cases; the explicit null branch is new.

Frontend:
- No changes needed — the backend sends `username: "Deleted user"` in the DTO.

## Risk

- **V7 migration touches many FKs.** Dev DB is wipe-and-recreate per the user's explicit "don't worry about migrating data" stance. No production concern.
- **Constraint-name drift.** V3 used both explicit `fk_...` names and auto-generated names on inline FKs. The plan step enumerates them via `\d+` on each table before writing the final migration body — if any name differs from what's expected, the migration fails at `DROP CONSTRAINT` and we fix narrowly before committing.
- **JWT existence check adds a DB hit per request.** Acceptable at hackathon scale; if it becomes a hotspot later, a short TTL cache can be added.

## Plan outline

1. V7 Flyway migration rewriting the FKs (dropping + re-adding with correct `ON DELETE` semantics)
2. `UserService.changePassword` + `UserService.deleteAccount` + `MessageService.toDto` null-author fallback + tests
3. `UserController` PATCH + DELETE endpoints + controller tests
4. `JwtAuthenticationFilter` existence guard + test
5. `AccountDeletionFlowIntegrationTest`
6. `accountService.ts`
7. `ChangePasswordModal` + `DeleteAccountModal` + ProfileMenu wiring
8. Playwright `account-management.spec.ts`
9. `FEATURES_ROADMAP.md` update — Feature #8 complete

## Verification checklist

- [ ] `./gradlew test` green — backend suite + new integration test
- [ ] `npm run build` clean, `npm test -- --run` green
- [ ] Backend docker image rebuilt (new endpoints + new migration)
- [ ] Browser smoke: change password → log out → log in with new; delete account → redirect to `/login`, same email re-registers
- [ ] Playwright green
- [ ] `FEATURES_ROADMAP.md` updated — Feature #8 COMPLETE, Feature #9 still TODO
