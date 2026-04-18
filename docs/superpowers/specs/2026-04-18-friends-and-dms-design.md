# Feature #3: Friends & Direct Messaging — Design Specification

**Status:** Approved
**Date:** 2026-04-18
**Deadline:** 2026-04-20 12:00 UTC

## Goal

Add a contacts/friends system and one-to-one direct messaging to the chat application, fulfilling requirements 2.3 (Contacts/Friends) and 2.5.1 (Personal Messaging) from the requirements document.

Users can send friend requests by username or from a room's user list, accept or reject them, manage their friends list, and exchange real-time direct messages with friends. User-to-user ban is supported and enforces the rule from 2.3.6: users may exchange personal messages only if they are friends and neither has banned the other.

## Scope

**In scope**
- Friend requests: send by username, accept, reject, cancel outgoing
- Friend list management: list, remove friend
- User-to-user ban (terminates friendship, blocks new DMs, existing DM history remains visible but frozen)
- One-to-one direct messaging with real-time WebSocket delivery
- Direct message history with cursor-based pagination
- Friend request action in room user list (discoverability from existing rooms)
- Persistence: messages to offline users remain available when they reconnect (via history load)

**Out of scope** (deferred to later features)
- Message editing, deletion, replies (spans rooms + DMs — separate slice)
- Attachments in DMs (part of Feature #5: File/Image Sharing)
- Unread counts, read receipts, typing indicators
- Unbanning (requirements do not mandate it)
- Retroactive "you were banned" notification to the banned user (silent by design)

## Architecture

REST for state-changing and list operations; WebSocket STOMP for real-time delivery, using Spring's user destinations (`/user/queue/...`) so each user's session receives its own events without per-conversation topics.

Separate tables for friendships, bans, direct conversations, and direct messages — no changes to the existing `messages` / `chat_rooms` tables. Feature #2 code stays intact.

The same migration that introduces the new tables also converts every existing `TIMESTAMP` column to `TIMESTAMPTZ` so all time columns in the project share one type.

## Data Model

All new tables use UUID primary keys and `TIMESTAMPTZ` for time columns.

### `friendships` — pending requests + accepted friendships

```sql
id              UUID PK DEFAULT gen_random_uuid()
requester_id    UUID NOT NULL REFERENCES users(id)
addressee_id    UUID NOT NULL REFERENCES users(id)
status          VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'accepted'))
created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
UNIQUE(requester_id, addressee_id)
INDEX (addressee_id, status)
INDEX (requester_id, status)
```

- List my friends: `WHERE (requester_id = me OR addressee_id = me) AND status = 'accepted'`
- My incoming pending: `WHERE addressee_id = me AND status = 'pending'`
- Reject is a `DELETE` (row can be re-created later). Accept is an `UPDATE status = 'accepted'`.
- If A→B request is pending and B sends a request to A, auto-accept both sides (service-layer logic).

### `user_bans` — unidirectional bans

```sql
id           UUID PK DEFAULT gen_random_uuid()
banner_id    UUID NOT NULL REFERENCES users(id)
banned_id    UUID NOT NULL REFERENCES users(id)
created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
UNIQUE(banner_id, banned_id)
INDEX (banned_id)
```

- Creating a ban also deletes any friendship row between the two users in the same transaction.
- DM send checks that no ban exists in either direction between sender and recipient.

### `direct_conversations` — one row per user pair

```sql
id          UUID PK DEFAULT gen_random_uuid()
user1_id    UUID NOT NULL REFERENCES users(id)   -- canonical: lower UUID
user2_id    UUID NOT NULL REFERENCES users(id)   -- higher UUID
created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
UNIQUE(user1_id, user2_id)
CHECK (user1_id < user2_id)
```

- Canonical ordering guarantees one conversation per pair.
- Created lazily on first message send.

### `direct_messages` — mirror of `messages` scoped to a conversation

```sql
id               UUID PK DEFAULT gen_random_uuid()
conversation_id  UUID NOT NULL REFERENCES direct_conversations(id) ON DELETE CASCADE
sender_id        UUID NOT NULL REFERENCES users(id)
text             VARCHAR(3072) NOT NULL
created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
INDEX (conversation_id, created_at DESC)
```

### Migration

File: `V3__friends_and_dms.sql`

- Creates the four tables above
- Converts every existing `TIMESTAMP` column in `users`, `chat_rooms`, `messages`, `room_members` to `TIMESTAMPTZ` using `ALTER COLUMN ... TYPE TIMESTAMPTZ USING ... AT TIME ZONE 'UTC'`

### JPA mapping

All time fields in JPA entities are `java.time.OffsetDateTime` (not `LocalDateTime`, which has no zone). Existing Feature #1/#2 entities are updated to use `OffsetDateTime` in the same change.

## Backend

### Package layout (feature-based flat, following Feature #2 conventions)

```
features/friendships/
  Friendship.java
  FriendshipRepository.java
  FriendshipService.java
  FriendshipController.java
  FriendshipServiceTest.java
  FriendshipControllerTest.java

features/bans/
  UserBan.java
  UserBanRepository.java
  UserBanService.java
  UserBanController.java
  UserBanServiceTest.java

features/dms/
  DirectConversation.java
  DirectConversationRepository.java
  DirectMessage.java
  DirectMessageRepository.java
  ConversationService.java
  DirectMessageService.java
  DirectMessageController.java
  DirectMessageServiceTest.java
  DirectMessageControllerTest.java

shared/websocket/
  DirectMessageHandler.java
  FriendshipNotificationHandler.java
```

### REST endpoints

**Friendships**

| Method | Path                                       | Body                      | Description                                  |
|--------|--------------------------------------------|---------------------------|----------------------------------------------|
| POST   | `/api/friendships/requests`                | `{ "username": "..." }`   | Send friend request                          |
| GET    | `/api/friendships/requests?direction=incoming\|outgoing` | —                         | List pending requests                        |
| POST   | `/api/friendships/requests/{id}/accept`    | —                         | Accept (only addressee)                      |
| POST   | `/api/friendships/requests/{id}/reject`    | —                         | Reject (deletes row)                         |
| GET    | `/api/friendships`                         | —                         | List accepted friends (with usernames)       |
| DELETE | `/api/friendships/{friendUserId}`          | —                         | Unfriend                                     |

**Bans**

| Method | Path               | Body                  | Description                           |
|--------|--------------------|-----------------------|---------------------------------------|
| POST   | `/api/bans`        | `{ "userId": "..." }` | Ban user, delete any friendship row   |
| GET    | `/api/bans`        | —                     | List users I've banned                |

**Direct messages**

| Method | Path                                           | Body                | Description                                |
|--------|------------------------------------------------|---------------------|--------------------------------------------|
| GET    | `/api/dms/conversations`                       | —                   | List my conversations with last-message preview |
| GET    | `/api/dms/with/{otherUserId}`                  | —                   | Get or create conversation, returns id     |
| GET    | `/api/dms/{conversationId}/messages?before=&limit=50` | —            | Cursor-paginated history                   |
| POST   | `/api/dms/{conversationId}/messages`           | `{ "text": "..." }` | Send via REST (validates friendship+ban)   |

### WebSocket (Spring user destinations)

- Client subscribes `/user/queue/dms` — receives DMs addressed to them
- Client subscribes `/user/queue/friend-events` — receives request-created, friendship-accepted, friendship-removed events
- Client sends `/app/dms/{conversationId}/message` — `DirectMessageHandler` validates, persists, and pushes to both participants' `/user/queue/dms`

### Validation & invariants (all enforced in services)

- `FriendshipService.sendRequest`: can't request self; can't request if already friends or if a ban exists either direction; if inverse pending request exists → auto-accept both sides
- `FriendshipService.accept`: only the addressee may accept; request must be in `pending` status
- `UserBanService.ban`: can't ban self; within one transaction: insert ban row + delete any friendship row between the pair
- `DirectMessageService.send`: friendship must exist with status `accepted`; no ban in either direction; text non-empty and ≤3072 chars

### User resolution cleanup

Feature #2 controllers currently use `UUID.randomUUID()` as a placeholder for the authenticated user ID. This feature introduces proper resolution via `UserService.findByUsername(authentication.getName())` and retrofits Feature #2 controllers (`ChatRoomController`, `MessageController`, `ChatMessageHandler`) to use real user IDs from the authentication principal.

## Frontend

Follows Feature #2 patterns (services → hooks → components → pages). Reuses `MessageList` and `MessageInput` from Feature #2 unchanged.

### Pages

```
pages/
  FriendsPage.tsx              # friends + incoming/outgoing requests + send-request form
  DirectMessagesPage.tsx       # list of DM conversations
  DirectChatPage.tsx           # DM chat (reuses MessageList + MessageInput)
```

### Components

```
components/
  AppSidebar.tsx               # persistent left nav: Rooms / Friends / DMs
  FriendsList.tsx              # friends with remove/ban/message actions
  FriendRequestList.tsx        # incoming (Accept/Reject) and outgoing (Cancel)
  SendFriendRequestForm.tsx    # username input + submit
  ConversationList.tsx         # DM conversation list with last-message preview
  RoomMembersPanel.tsx         # side panel on ChatPage: members + "Add Friend"
```

### Services

```
services/
  friendshipService.ts
  banService.ts
  directMessageService.ts
```

`websocketService.ts` gets a new helper `subscribeUserQueue(destination, callback)` for Spring user destinations.

### Hooks

```
hooks/
  useFriends.ts                # accepted friends + remove/ban
  useFriendRequests.ts         # incoming + outgoing with accept/reject/cancel
  useDirectConversations.ts    # conversation list + reorder on new message
  useDirectMessages.ts         # history with cursor pagination (mirrors useRoomMessages)
  useDirectMessageSocket.ts    # top-level subscription to /user/queue/dms
```

### Routes

- `/friends` — FriendsPage
- `/dms` — DirectMessagesPage
- `/dms/:conversationId` — DirectChatPage

All wrapped in `AppSidebar`, protected by `AuthGuard`.

### WebSocket subscriptions on app mount

A top-level hook (e.g., `useAppRealtime`) subscribes to both user queues once, and dispatches into the relevant stores/state:

- `/user/queue/dms` — new DM → append to active chat if open + update conversation list preview/order
- `/user/queue/friend-events` — friend-request-created / friendship-accepted / friendship-removed → update friends/requests state

### ChatPage update

`ChatPage.tsx` gains a right-side `RoomMembersPanel` that lists room members and offers an "Add Friend" button next to each non-friend. The button calls `friendshipService.sendRequest(username)`.

## Key Flows

### Send friend request
1. User submits username in `SendFriendRequestForm` → `friendshipService.sendRequest(username)`
2. `POST /api/friendships/requests` → backend looks up user by username → validates (not self, not already friends, not banned either direction)
3. If inverse pending exists → auto-accept both
4. Otherwise insert pending row
5. Backend pushes `friend-request-created` to addressee's `/user/queue/friend-events`
6. Addressee's `useFriendRequests` updates — request appears in their incoming list

### Accept friend request
1. Addressee clicks Accept → `POST /api/friendships/requests/{id}/accept`
2. Backend updates status to `accepted`
3. Backend pushes `friendship-accepted` to both users' `/user/queue/friend-events`
4. Both users' `useFriends` updates

### Ban a user
1. User clicks Ban in `FriendsList` or `RoomMembersPanel` → `POST /api/bans { userId }`
2. Backend transaction: insert ban + delete friendship row if any
3. Backend pushes `friendship-removed` to the banned user (silent, no "you were banned" hint)
4. Further DM sends between the pair fail with 403 at send time

### Send DM
1. User types in MessageInput on DirectChatPage → WebSocket `/app/dms/{conversationId}/message`
2. `DirectMessageHandler` validates participant, friendship, and no ban
3. Persist `DirectMessage` row
4. Push `ChatMessageDTO` to both participants' `/user/queue/dms`
5. Both clients' `useDirectMessageSocket` receives → append to active chat or update conversation preview

### Receive DM while not on chat page
1. Client subscribed to `/user/queue/dms` on app mount
2. New message arrives → `useDirectConversations` updates list (preview + reorder by recency)
3. User sees updated conversation at the top of DirectMessagesPage

### Add friend from room user list
1. `RoomMembersPanel` fetches room members
2. User clicks "Add Friend" next to a member → `friendshipService.sendRequest(username)` (same endpoint as FriendsPage)
3. Member sees request in their FriendsPage via `/user/queue/friend-events`

### Offline delivery (per 2.5.6)
- DB is source of truth; messages are persisted regardless of recipient connection
- Recipient reconnects → loads conversation via `GET /api/dms/{conversationId}/messages`
- No "pending delivery" state

### Error and edge cases
- DM to non-friend → 403 "You must be friends to send a direct message"
- DM when ban exists either direction → 403, no mention of ban
- Accept already-accepted or deleted request → 404
- Ban self → 400
- Friend request with unknown username → 404 "User not found"

## Testing

Follows the TDD pattern from Feature #2. Integration tests use testcontainers PostgreSQL, not H2, matching project conventions.

- Service-level unit tests with Mockito for each service (friendship, ban, conversation, direct message)
- Controller integration tests with `@SpringBootTest` and `@WithMockUser`
- WebSocket handler tests for DM routing and friendship-event push
- Test data generated via Instancio where possible
- End-to-end scenario test: A and B become friends → A sends DM → B receives via WebSocket + history fetch

## Non-Goals / Notes

- No UI for unbanning — requirements do not call for it
- No "typing indicator" or read receipts — not required by spec
- Display of banned users in the banner's own UI is out of scope (can be a future list view)
- Large existing `messages` table is not migrated; DMs live in their own `direct_messages` table to keep Feature #2 untouched
