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

## In-Progress Features

### Feature #3: Friends & Direct Messaging 🔄
Combined scope per 2026-04-18 brainstorming (friends + DMs + user-to-user ban + room-user-list discovery) because requirement 2.3.6 couples personal messaging to friendship.
- Friend requests: send by username, accept, reject, cancel; auto-accept on inverse pending
- Friend list + remove friend
- User-to-user ban (terminates friendship, blocks DMs, freezes history)
- One-to-one direct messaging with real-time WebSocket delivery
- Direct message history with cursor pagination
- Room user list with "Add Friend" action
- Schema retrofit: `users.id` Integer → UUID; FK constraints from Feature #2 tables; all time columns → TIMESTAMPTZ; JPA fields → OffsetDateTime; real JWT auth filter (replacing placeholder user IDs)
- Spec: `docs/superpowers/specs/2026-04-18-friends-and-dms-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-friends-and-dms.md` (24 tasks across 7 sections)
- Progress: Task 1/24 complete (V3 Flyway migration)
- **Status: IN PROGRESS**

## Planned Features

### Feature #4: Private Rooms, Room Moderation & Invitations
- Private rooms (not in public catalog, join by invitation)
- Room owner + admin roles
- Admins: delete messages, remove/ban members from room, manage admin list
- Owner: delete room, cannot be removed
- Room invitations for private rooms
- Room ban list (removed users treated as banned)
- **Status: TODO**

### Feature #5: Message Content Enhancements
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
- **Completed:** 2/8 (Features #1, #2)
- **In progress:** 1/8 (Feature #3 — Task 1/24 done)
- **Remaining:** 5/8 (Features #4–#8)
