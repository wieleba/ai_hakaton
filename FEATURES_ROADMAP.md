# Chat Application — Features Roadmap

**Deadline:** Monday, April 20, 2026 12:00 UTC

## Completed Features

### Feature #1: User Registration & Authentication ✅
- Self-registration with email, password, unique username
- Login with JWT token-based authentication
- Persistent login across browser close/reopen
- BCrypt password hashing
- Spec: none (pre-brainstorming)
- **Status: COMPLETE**

### Feature #2: Public Chat Rooms & Real-Time Messaging ✅
- Create public chat rooms (name + optional description)
- List public rooms with pagination
- Join / leave chat rooms (owner cannot leave)
- Real-time messaging via Spring WebSocket + STOMP (per-room topics)
- Message history with cursor-based pagination (designed for 100K+ messages)
- Persistent message storage (PostgreSQL, UUID PKs)
- Backend TDD coverage (service tests via Instancio, controller tests via MockMvc, end-to-end integration test)
- Lombok adopted to reduce entity/service boilerplate
- Frontend: RoomListPage, ChatPage, MessageList, MessageInput, RoomCreateModal, `useRoom`/`useRoomMessages`/`useWebSocket` hooks
- Spec: `docs/superpowers/specs/2026-04-18-chat-rooms-messaging-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-chat-rooms-messaging.md`
- **Status: COMPLETE**

### Feature #3: Friends & Direct Messaging ✅
Combined scope per 2026-04-18 brainstorming (friends + DMs + user-to-user ban + room-user-list discovery) because requirement 2.3.6 couples personal messaging to friendship.
- Friend requests: send by username, accept, reject, cancel; auto-accept on inverse pending
- Friend list + remove friend
- User-to-user ban (terminates friendship, blocks DMs)
- One-to-one direct messaging with real-time WebSocket delivery (Spring user destinations)
- Direct message history with cursor pagination
- Room user list with "Add Friend" action
- Schema retrofit: `users.id` Integer → UUID; FK constraints from Feature #2 tables; all time columns → TIMESTAMPTZ; JPA fields → OffsetDateTime; real `JwtAuthenticationFilter` replacing placeholder user IDs; `AppSidebar` left-nav
- Backend: 96 tests passing (unit + controller + end-to-end `FriendsAndDmsFlowIntegrationTest`)
- Frontend: 45 tests passing (including smoke test for FriendsPage)
- Spec: `docs/superpowers/specs/2026-04-18-friends-and-dms-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-friends-and-dms.md` (24 tasks across 7 sections — all complete)
- **Status: COMPLETE**

### Feature #4: Private Rooms, Room Moderation & Invitations ✅
- Private rooms (not in public catalog, join by invitation; 404-cloaked from non-members)
- Owner + admin roles on RoomMember (`role` column on room_members, ROLE_MEMBER/ROLE_ADMIN)
- Admins: kick members (kick = ban), promote/demote other admins, view and clear the ban list
- Owner: delete room (cascades to members + invitations + bans), cannot leave or be demoted
- Room invitations for private rooms: send by username, accept/decline/cancel; incoming invitations tab
- Room ban list with unban action (kicked users are treated as banned)
- Schema: V4 migration — `role` on `room_members`, `room_bans`, `room_invitations`
- Backend: 3-scenario `RoomModerationFlowIntegrationTest` (private lifecycle, private-without-invite, banned-public-rejoin) + per-service TDD
- Frontend: visibility picker, tabbed RoomListPage (Public | My rooms), RoomMembersPanel admin controls with role badges, InviteUserModal, BanListPanel, DeleteRoomDialog
- Playwright: full lifecycle E2E (create private → invite → accept → kick → unban → delete)
- Spec: `docs/superpowers/specs/2026-04-18-room-moderation-and-invitations-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-room-moderation-and-invitations.md` (19 tasks across 7 sections — all complete)
- **Status: COMPLETE**

### Execution #5: App Shell Refactor (Appendix A layout) ✅
- Top menu (Public Rooms / Contacts / Sessions stub / Profile ▼)
- Left tree sidebar (Rooms ▸ Public / Private, Contacts, Create room, Search dropdown)
- Right `RoomMembersPanel` restructured: presence groupings (Online / AFK / Offline, all Offline until Feature #7), admin buttons at bottom (`Invite user`, `Manage room`)
- `ManageRoomModal` (tabbed: Members / Invitations / Banned / Settings) — owns kick/promote/demote, invitation cancel, unban, delete
- `/api/search` backend endpoint over public rooms + users (excludes caller / member rooms)
- `MessageInput` gains an empty `ComposerActions` slot for Features #5/#6
- `AppSidebar` removed; `App.tsx` nests authenticated routes under a single `AppShell` layout route
- Playwright lifecycle E2E (`app-shell.spec.ts`, 3 scenarios) + existing suite (8 scenarios) kept green
- Spec: `docs/superpowers/specs/2026-04-18-app-shell-refactor-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-app-shell-refactor.md` (12 tasks — all complete)
- **Status: COMPLETE**

## Planned Features

### Feature #5 (planned content): Message Content Enhancements
> Execution note: this now lands AFTER the app shell refactor which claimed the execution #5 slot.
- Multi-line + emoji (plain text already supported)
- Replies / quoted messages
- Message editing with "edited" indicator
- Message deletion (by author or room admin)
- Applies to both rooms and DMs
- **Status: TODO**

### Feature #6: Attachments (File & Image Sharing)
- Upload images and files (rooms + DMs)
- In-chat image previews
- File downloads
- Storage tied to room/conversation lifetime (deleted with parent)
- **Status: TODO**

### Feature #7: User Presence & Session Management
- Presence states: online / AFK (≥1 min inactive) / offline
- Multi-tab support (online if active in ANY tab)
- Active session list (browser/IP), logout from specific sessions
- **Status: TODO**

### Feature #8: Account Management
- Password reset
- Password change (for logged-in users)
- Account deletion cascades (owned rooms + messages + files deleted; memberships removed)
- **Status: TODO**

## Key Architecture Notes
- **Backend:** Spring Boot 3.5.12, Java 25, Gradle 9.4.1, PostgreSQL 15, Flyway
- **Frontend:** React 19, TypeScript, Vite, axios, @stomp/stompjs + sockjs-client
- **Real-time:** Spring WebSocket + STOMP (per-room topics for rooms; per-user queues for DMs and friend events)
- **Database:** UUID primary keys everywhere; TIMESTAMPTZ for all time columns
- **Tests:** TDD; testcontainers PostgreSQL (not H2); Instancio for test data
- **Target:** up to 300 simultaneously connected users

## Progress
- **Completed:** 5 execution slots (Features #1, #2, #3, #4, App Shell Refactor)
- **In progress:** 0
- **Remaining:** 4 (Message Content, Attachments, Presence/Sessions, Account Management)
