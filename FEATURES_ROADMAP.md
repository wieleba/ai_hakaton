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

### Feature #5 (content): Message Content Enhancements ✅
- Reply / quote-to-message — flat `reply_to_id` reference; quote header with author + 100-char preview; `[deleted]` placeholder when parent is soft-deleted
- Author edit with `(edited)` indicator — author-only; blocked on tombstone; `editedAt` timestamp on the DTO
- Author soft-delete — author-only; idempotent; renders as muted italic `Message deleted`; original text retained in DB for audit
- Applies uniformly to room messages and direct messages (same schema delta, same service methods, mirrored DTOs)
- WebSocket payload promoted to tagged union `{type: CREATED | EDITED | DELETED, message}` on both `/topic/room/{roomId}` and `/user/{uuid}/queue/dms`
- Frontend: `MessageItem` extraction, `MessageActionsMenu` hover bar, `ReplyPill` composer chip, `InlineMessageEditor`, `ComposerActions` slot finally used
- Multi-line + Unicode emoji already worked (native textarea input)
- Backend: V5 migration + 178-test backend suite green (new `MessageContentFlowIntegrationTest` for rooms + DMs)
- Playwright: 12-scenario suite green, including new `message-content.spec.ts` two-browser lifecycle
- Spec: `docs/superpowers/specs/2026-04-18-message-content-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-message-content.md` (13 tasks — all complete)
- **Status: COMPLETE**

### Feature polish: Emoji Picker + Message Reactions ✅
- Emoji picker in composer (`emoji-picker-react`) — 😀 button in the `ComposerActions` slot; inserts at the current textarea caret
- Message reactions — click 😀+ from the hover menu to pick any emoji; clickable chips appear below the message body showing `[emoji count]` with blue highlight when `reactedByMe`
- Schema: V6 migration — `message_reactions` + `direct_message_reactions` tables with UNIQUE `(message_id, user_id, emoji)`
- Backend: `toggleReaction(messageId, callerId, emoji)` on both `MessageService` + `DirectMessageService`; POST `/api/rooms/{rid}/messages/{mid}/reactions` and `/api/dms/{cid}/messages/{mid}/reactions`
- DTOs gain `reactions: List<ReactionSummary>` with `{emoji, count, reactedByMe}` aggregation
- Real-time propagation piggybacks on the existing `EDITED` WS envelope (frontend `upsertMessage` merges in place)
- Backend tests: toggle add/remove + multi-user/multi-emoji aggregation + non-participant rejection for DMs
- Playwright: 12-scenario suite kept green
- Spec: `docs/superpowers/specs/2026-04-19-emoji-and-reactions-design.md`
- **Status: COMPLETE**

### Chat UX fix: newest message at bottom ✅
- `MessageList` displays oldest-first, newest-last (classic chat layout); hook state stays newest-first so WS event handling is unchanged
- Scroll snaps to bottom only on a genuinely new message (newest id changed); loading older history preserves scroll position

### Feature #10: YouTube link embeds in messages ✅
- Detect YouTube URLs in message text (youtube.com/watch, youtu.be, youtube.com/shorts, youtube.com/embed)
- Inline `<iframe>` player using `youtube-nocookie.com` (privacy mode — no cookies until play)
- Frontend-only: `extractYouTubeIds` utility + `YouTubeEmbed` component + `MessageItem` integration
- Works retroactively on all historical messages without any migration
- Multiple videos per message supported (deduped, rendered in encounter order)
- Extra URL params (e.g. `&t=30s`) ignored; embeds play from the start
- Tests: 9 unit-test cases covering URL shape variants, dedup, order, non-matches
- **Status: COMPLETE**

### Feature #6: Attachments (File & Image Sharing) ✅
- One attachment per chat-room or direct-message (multi-attachment deferred)
- S3-compatible object storage (MinIO in docker-compose; any S3 endpoint in prod — every backend replica sees the same bytes)
- Multipart `POST /api/rooms/{rid}/messages` and `/api/dms/{cid}/messages` accept `text` + `file`; plain text still goes over WebSocket
- Backend-proxied `GET /api/attachments/{id}/content` with room-membership / DM-participation auth; `Content-Disposition: inline` for image/* allow-list, `attachment` for everything else
- 10 MB cap; MIME allow-list — png/jpeg/gif/webp/pdf/txt/zip (SVG / HTML / executables rejected)
- Soft-delete of a message unlinks the S3 object; hard cascade (room/conversation delete) removes DB rows via FK CASCADE (S3 objects may orphan on hard cascade — known limitation)
- Frontend: `ComposerAttachButton` + `AttachmentPreviewChip` + `AttachmentRenderer` (inline image or download link); `MessageItem` renders either
- Backend tests: `InMemoryStorageServiceTest`, `AttachmentControllerTest` (5 scenarios), `AttachmentFlowIntegrationTest` (3 scenarios)
- Playwright: `attachments.spec.ts` — two-client image upload lifecycle
- Spec: `docs/superpowers/specs/2026-04-19-attachments-design.md`
- Plan: `docs/superpowers/plans/2026-04-19-attachments.md` (12 tasks — all complete)
- **Status: COMPLETE**

### Feature #7: User Presence ✅
- Three states (ONLINE / AFK / OFFLINE) aggregated per user from any number of active WS sessions
- Redis-backed session registry + pub/sub for cross-instance fan-out (every replica sees every state change)
- Client AFK detection: throttled activity listeners + 60s idle threshold; heartbeat every 30s; server watchdog evicts sessions >90s stale
- REST snapshot `GET /api/presence?userIds=...` + STOMP topic `/topic/presence` delta stream
- Frontend: `usePresence` shared-state hook + `useAfkTracking` installed once in `AppShell`; `SideTreeContactList` + `RoomMembersPanel` render live states
- Backend tests: `InMemoryPresenceServiceTest` (10), `PresenceEventListenerTest` (4), `PresenceMessageHandlerTest` (4), `PresenceWatchdogTest` (2), `PresenceControllerTest` (2)
- Sessions-management (active-session list, log out from specific session) split to Feature #12
- Spec: `docs/superpowers/specs/2026-04-19-presence-design.md`
- Plan: `docs/superpowers/plans/2026-04-19-presence.md` (11 tasks — all complete)
- **Status: COMPLETE**

### Feature #12: Sessions Management (split out of #7) ✅
- Active WebSocket session list per user (browser user-agent, IP, connected-at, last-seen) — `PresenceEntry` carries per-session metadata; surfaced via `GET /api/sessions`
- "Log out from this session" action — `DELETE /api/sessions/{sessionId}` revokes the JWT, disconnects the WS, and fans out an `EVICTED` frame on `/user/{uuid}/queue/sessions`
- "Log out everywhere else" — `DELETE /api/sessions/others` rejects stale `X-Session-Id`, revokes every sibling token, returns `revokedCount`
- Token revocation backed by Redis `revoked_token:{sha256(jwt)}` set (TTL = remaining JWT lifetime); enforced on both REST (`JwtAuthenticationFilter`) and WS (`CONNECT` interceptor)
- Frontend: `SessionsPage` replaces the stub (live table with per-row log-out + bulk log-out-others), `useEvictedSessionWatcher` redirects the current tab to `/login` when it's the one being kicked, axios interceptor attaches `X-Session-Id` on every request
- Backend tests: `TokenRevocationServiceTest`, `SessionsControllerTest`, `SessionsFlowIntegrationTest` (list / log-out-self / log-out-others / stale-session guard)
- Completed 2026-04-19
- Spec: `docs/superpowers/specs/2026-04-19-sessions-management-design.md`
- Plan: `docs/superpowers/plans/2026-04-19-sessions-management.md` (5 tasks — all complete)
- **Status: COMPLETE**

## Planned Features

### Feature #8: Account Management ✅
- Password change for logged-in users — `PATCH /api/users/me/password` requires old password and min-8-char new password (403 on wrong old, 400 on too short)
- Account deletion — `DELETE /api/users/me` permanently removes the user row; V7 migration cascades through owned rooms + memberships + friendships + DMs + invitations + bans + reactions
- Messages sent in rooms the deleted user did not own survive with `user_id = NULL` and render as "Deleted user" (backend service fallback)
- `JwtAuthenticationFilter` rejects tokens for deleted users with 401 on the next request
- Frontend: `ChangePasswordModal` and `DeleteAccountModal` wired into the profile dropdown; "Type DELETE to confirm" gate on deletion
- Backend tests: `UserServiceTest`, `UserControllerTest`, `UserControllerSecurityTest`, `JwtAuthenticationFilterExistenceTest`, `AccountDeletionFlowIntegrationTest`
- Playwright: `account-management.spec.ts` — change-then-login + delete-then-reregister
- Spec: `docs/superpowers/specs/2026-04-19-account-management-design.md`
- Plan: `docs/superpowers/plans/2026-04-19-account-management.md` (9 tasks — all complete)
- **Status: COMPLETE**

### Feature #9: Password Reset (split out of #8)
- Forgot-password request by email → time-limited reset token
- Email delivery (SMTP / local dev mailhog fallback)
- Reset page that consumes the token + sets a new password
- Split out of Feature #8 because the email/SMTP infrastructure is a full subsystem; not in scope for the hackathon deadline
- Task 1 (mail infrastructure: spring-boot-starter-mail, MailHog docker service, NoopMailSender test-profile bean) landed
- Task 2 (backend: V10 migration, token entity/repository, email builder, service with 2-minute cooldown + session revocation on confirm, controller, SecurityConfig permits `/api/password-reset/**`, 13 new tests) landed
- **Status: IN PROGRESS** (Tasks 1–2/3 complete — backend endpoints live; Task 3 adds frontend pages)

### Feature #11: Server-side embed metadata (split out of #10)
- Parse embed URLs (YouTube, future: Twitter/X, Spotify, generic OG) on send
- Persist `message_embeds` table per message with `kind`, `source_url`, `canonical_id`, cached `title`, `thumbnail_url`
- Expose on DTOs so clients get pre-parsed metadata instead of each re-running regex
- Enables: server-side moderation (ban a video across rooms), richer previews (thumbnails + titles), search-by-embed-type
- Split out of Feature #10 because the frontend-only approach gets us the primary UX win (inline video player) with zero schema change; server-side metadata is an enhancement, not a blocker
- **Status: TODO**

## Key Architecture Notes
- **Backend:** Spring Boot 3.5.12, Java 25, Gradle 9.4.1, PostgreSQL 15, Flyway
- **Frontend:** React 19, TypeScript, Vite, axios, @stomp/stompjs + sockjs-client
- **Real-time:** Spring WebSocket + STOMP (per-room topics for rooms; per-user queues for DMs and friend events)
- **Database:** UUID primary keys everywhere; TIMESTAMPTZ for all time columns
- **Tests:** TDD; testcontainers PostgreSQL (not H2); Instancio for test data
- **Target:** up to 300 simultaneously connected users

## Progress
- **Completed:** 11 execution slots (Features #1, #2, #3, #4, App Shell Refactor, Message Content, Account Management, Attachments, YouTube Embeds, Presence, Sessions Management) + polish (emoji picker + reactions, chat ordering)
- **In progress:** 1 (Password Reset — Tasks 1–2/3 landed: mail infra + backend endpoints)
- **Remaining:** 2 (Password Reset Task 3, Server-side Embed Metadata)
