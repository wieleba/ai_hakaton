# Feature #2: Public Chat Rooms & Real-Time Messaging

**Date:** 2026-04-18  
**Deadline:** Monday 2026-04-20 12:00 UTC  
**Status:** Design Phase

---

## Overview

Implement public chat rooms and real-time messaging to deliver the core chat experience. Users can create public rooms, join/leave freely, send and receive messages in real-time via WebSocket, and browse message history with cursor-based pagination.

**Key Innovation:** Hybrid REST + WebSocket architecture. REST handles stateless operations (room/message data), WebSocket handles real-time delivery via STOMP topics per room.

---

## Requirements (from Section 2.4-2.5 of requirements v3)

### Chat Rooms (Scoped to Public Only)
- Any authenticated user can create a public chat room
- Room name must be unique
- Public rooms are discoverable in a catalog showing name and member count
- Users can join/leave public rooms freely (unless banned - deferred)
- Users can search for rooms
- Room owner created implicitly when user creates room
- Room owner cannot leave (can only delete)

### Messaging
- Users in a room can send/receive messages in real-time
- Message content: plain text only (emoji/attachments deferred)
- Max message size: 3 KB
- UTF-8 support required
- Messages stored persistently, displayed in chronological order
- Users can scroll through old history (infinite scroll)
- Delivery latency: <3 seconds
- At least 10,000+ messages per room supported

---

## Architecture

### Design Pattern: Option A - WebSocket-First with REST History

**REST API Responsibilities:**
- Create/list/join/leave rooms
- Fetch message history (cursor-based pagination)
- Access control (check membership before returning messages)

**WebSocket STOMP Responsibilities:**
- Real-time message delivery via room topics
- User subscribes to `/topic/room/{roomId}` when they join
- Messages broadcast to all subscribers in that topic
- Each message persisted to database before broadcast

**Separation of Concerns:**
- Real-time updates: WebSocket (low latency, per-user)
- State/history: REST (stateless, cacheable, queryable)
- Membership: Database (access control, presence tracking)

### Technology Stack

**Backend:**
- Spring Boot 3.x with Spring WebSocket (STOMP)
- PostgreSQL with Flyway migrations
- JPA/Hibernate for ORM
- Java 25, Gradle 9.4.1

**Frontend:**
- React 19 with TypeScript
- SockJS + STOMP client for WebSocket
- Axios for REST calls
- Vitest + React Testing Library

---

## Database Schema

All primary keys use **UUID v4** for security and scalability.

### Table: chat_rooms
```sql
CREATE TABLE chat_rooms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL UNIQUE,
  description TEXT,
  owner_id UUID NOT NULL REFERENCES users(id),
  visibility VARCHAR(50) NOT NULL DEFAULT 'public',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT valid_visibility CHECK (visibility IN ('public', 'private'))
);
```

### Table: messages
```sql
CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  text VARCHAR(3072) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_room_created 
  ON messages(room_id, created_at DESC);
```

### Table: room_members
```sql
CREATE TABLE room_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, user_id)
);

CREATE INDEX idx_room_members_user 
  ON room_members(user_id, room_id);
```

---

## Backend Components

### Entities

**ChatRoom.java**
```
- id: UUID
- name: String (unique)
- description: String (nullable)
- ownerId: UUID
- visibility: String (public/private)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

**Message.java**
```
- id: UUID
- roomId: UUID
- userId: UUID
- text: String (max 3KB)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

**RoomMember.java**
```
- id: UUID
- roomId: UUID
- userId: UUID
- joinedAt: LocalDateTime
```

### Repositories

**ChatRoomRepository** extends JpaRepository<ChatRoom, UUID>
- findAll()
- findByVisibility(String visibility)
- findByName(String name)
- findByOwnerId(UUID ownerId)
- existsByName(String name)

**MessageRepository** extends JpaRepository<Message, UUID>
- findByRoomIdOrderByCreatedAtDesc(UUID roomId, Pageable pageable)
- countByRoomId(UUID roomId)

**RoomMemberRepository** extends JpaRepository<RoomMember, UUID>
- findByRoomIdAndUserId(UUID roomId, UUID userId) → Optional
- findByRoomId(UUID roomId) → List
- deleteByRoomIdAndUserId(UUID roomId, UUID userId)
- existsByRoomIdAndUserId(UUID roomId, UUID userId) → boolean

### Services

**ChatRoomService**
```
- createRoom(String name, String description, UUID userId): ChatRoom
  * Validate: name not empty, not already exists
  * Create room, set owner_id = userId
  * Auto-add creator as room member
  * Return room

- listPublicRooms(int page, int limit): Page<ChatRoom>
  * Query: visibility = 'public'
  * Include member count in response
  * Sort by created_at DESC

- joinRoom(UUID roomId, UUID userId): void
  * Check: room exists and is public
  * Check: user not already member
  * Add to room_members table
  * Throw exception if already member or room doesn't exist

- leaveRoom(UUID roomId, UUID userId): void
  * Check: room exists
  * Check: user is not owner
  * Remove from room_members
  * Throw exception if user is owner

- getRoomMembers(UUID roomId): List<UserDTO>
  * Query room_members by roomId
  * Join with users table
  * Return list of member info
```

**MessageService**
```
- sendMessage(UUID roomId, UUID userId, String text): Message
  * Validate: user is member of room
  * Validate: text not empty and ≤ 3KB
  * Create Message entity
  * Save to database
  * Return saved message

- getMessageHistory(UUID roomId, UUID messageCursor, int limit): List<Message>
  * Validate: user is member of room
  * If cursor is null: fetch last `limit` messages (most recent)
  * If cursor provided: fetch `limit` messages BEFORE cursor
  * Order by created_at DESC (newest first)
  * Return list

- getMessageCount(UUID roomId): long
  * Return count of messages in room
```

**RoomMemberService**
```
- isMember(UUID roomId, UUID userId): boolean
- addMember(UUID roomId, UUID userId): void
- removeMember(UUID roomId, UUID userId): void
- getMembers(UUID roomId): List<UUID>
```

### Controllers

**ChatRoomController** at `/api/rooms`
```
POST /api/rooms
- Request: { name: String, description?: String }
- Response: { id, name, description, ownerId, visibility, createdAt }
- Auth: Required
- Action: Create public room

GET /api/rooms
- Query: page=0, limit=20, search?=""
- Response: { content: [ChatRoom], totalElements, totalPages, currentPage }
- Auth: Required
- Action: List public rooms with optional search

POST /api/rooms/{id}/join
- Auth: Required
- Action: Add current user to room members
- Response: { success: true } or error

POST /api/rooms/{id}/leave
- Auth: Required
- Action: Remove current user from room members
- Response: { success: true } or error

GET /api/rooms/{id}/members
- Auth: Required
- Action: Get list of room members with user info
- Response: List<{ id, username, status }>
```

**MessageController** at `/api/rooms/{roomId}/messages`
```
GET /api/rooms/{roomId}/messages
- Query: before?=UUID (cursor), limit=50
- Response: List<{ id, userId, username, text, createdAt }>
- Auth: Required, user must be room member
- Action: Get message history with cursor pagination

POST /api/rooms/{roomId}/messages
- Request: { text: String }
- Response: { id, roomId, userId, text, createdAt }
- Auth: Required, user must be room member
- Action: Send message (also broadcasts via WebSocket)
```

### WebSocket Handler

**WebSocketConfig** (Spring configuration)
```
- Enable STOMP messaging
- Configure endpoint: /ws/chat
- Set app destination prefix: /app
- Set broker destination prefixes: /topic, /queue
```

**ChatMessageHandler** (message receiver)
```
@MessageMapping("/rooms/{roomId}/message")
public void handleMessage(@Payload ChatMessageDTO payload,
                          @DestinationVariable UUID roomId,
                          Principal principal) {
  // 1. Get user from principal
  // 2. Validate: user is member of roomId
  // 3. Create Message entity from payload
  // 4. Save to database
  // 5. Broadcast to /topic/room/{roomId}
  //    Payload: { id, userId, username, text, createdAt }
}
```

**Data Flow for Message Sending:**
1. Frontend sends to `/app/rooms/{roomId}/message` with text
2. Spring routes to ChatMessageHandler
3. Handler saves Message to DB
4. Handler sends to `/topic/room/{roomId}`
5. All subscribed clients receive the message
6. Frontend appends to message list in real-time

---

## Frontend Components

### Services

**roomService.ts**
```typescript
- createRoom(name: string, description?: string): Promise<Room>
- listPublicRooms(page: number, limit: number): Promise<PagedRooms>
- searchRooms(query: string): Promise<Room[]>
- joinRoom(roomId: string): Promise<void>
- leaveRoom(roomId: string): Promise<void>
- getRoomMembers(roomId: string): Promise<User[]>
```

**messageService.ts**
```typescript
- sendMessage(roomId: string, text: string): Promise<Message>
- getMessageHistory(roomId: string, before?: string, limit: number = 50): Promise<Message[]>
```

**websocketService.ts**
```typescript
- connect(token: string): void
- disconnect(): void
- subscribe(destination: string, callback: Function): void
- unsubscribe(subscription): void
- send(destination: string, payload: any): void
- isConnected(): boolean
```

### Custom Hooks

**useRoom.ts**
```typescript
- roomId: string
- currentRoom: Room | null
- joinedRooms: Room[]
- isLoading: boolean
- error: string | null
- joinRoom(roomId): Promise<void>
- leaveRoom(roomId): Promise<void>
- fetchRoom(roomId): Promise<void>
```

**useRoomMessages.ts**
```typescript
- messages: Message[]
- isLoading: boolean
- hasMore: boolean
- error: string | null
- loadMoreMessages(before?: string): Promise<void>
- addMessage(message): void
- clearMessages(): void
```

**useWebSocket.ts**
```typescript
- isConnected: boolean
- subscribe(roomId: string, onMessage: Function): void
- unsubscribe(roomId: string): void
- sendMessage(roomId: string, text: string): void
- error: string | null
```

### Components

**RoomListPage.tsx**
```
- Display list of public rooms (paginated)
- Search bar for room search
- "Create Room" button → modal with name/description input
- Click room → navigate to ChatPage
- Show member count per room
- Join button for each room
```

**ChatPage.tsx**
```
- Header: Room name, member count, leave button
- MessageList: Scrollable message area with infinite scroll
- MessageInput: Text input + send button
- Load room info on mount
- Subscribe to WebSocket topic
- Load initial message history
- Handle real-time message append
```

**MessageList.tsx**
```
- Display messages in chronological order (oldest at top)
- Virtual scrolling for performance (1000+ messages)
- Infinite scroll: load older messages when scrolled to top
- Auto-scroll to bottom on new message (if user at bottom)
- No forced scroll if user scrolled up to read history
```

**RoomCreateModal.tsx**
```
- Form: name (required), description (optional)
- Validation: name not empty, <255 chars
- Submit: create room, close modal, navigate to ChatPage
```

**MessageInput.tsx**
```
- Textarea for multiline input
- Submit button (or Ctrl+Enter)
- Max 3KB character limit display
- Disable submit if text empty
- Clear input after send
```

---

## Data Flow Diagrams

### Create Room Flow
```
User clicks "Create Room"
  ↓
RoomCreateModal opens
  ↓
User enters name, clicks submit
  ↓
POST /api/rooms { name, description }
  ↓
Backend creates ChatRoom, adds user as member, returns room
  ↓
Frontend navigates to ChatPage with new roomId
```

### Join Room Flow
```
User sees room in RoomListPage, clicks join
  ↓
POST /api/rooms/{id}/join
  ↓
Backend adds user to room_members
  ↓
Frontend navigates to ChatPage
  ↓
GET /api/rooms/{id}/messages?limit=50 (load initial history)
  ↓
SUBSCRIBE /topic/room/{id} (via WebSocket)
  ↓
Chat window displayed with messages and input active
```

### Send Message Flow
```
User types message, clicks send
  ↓
sendMessage(roomId, text)
  ↓
SEND /app/rooms/{id}/message { text }
  ↓
Backend ChatMessageHandler receives message
  ↓
Validate: user is room member
  ↓
Create Message entity, save to DB
  ↓
SEND /topic/room/{id} { id, userId, username, text, createdAt }
  ↓
Frontend receives via subscription
  ↓
Append message to MessageList in real-time
```

### Scroll Up to Load Old Messages Flow
```
User scrolls to top of MessageList
  ↓
useRoomMessages detects scroll threshold
  ↓
GET /api/rooms/{id}/messages?before={oldestMessageId}&limit=50
  ↓
Backend returns messages BEFORE cursor, ordered DESC
  ↓
Frontend prepends messages to list
  ↓
Virtual scroll maintains current scroll position
```

---

## Testing Strategy

### Backend Unit Tests

**ChatRoomServiceTest**
```
- testCreateRoom: create room, verify name unique, owner set
- testCreateRoomDuplicateName: should throw exception
- testListPublicRooms: paginated results
- testJoinRoom: add user to room_members
- testJoinRoomAlreadyMember: should throw exception
- testLeaveRoom: remove user from room_members
- testLeaveRoomAsOwner: should throw exception (owner can't leave)
- testRoomNameUnique: unique constraint enforced
```

**MessageServiceTest**
```
- testSendMessage: create message, persist to DB
- testSendMessageNotMember: should throw exception
- testSendMessageMaxSize: reject messages > 3KB
- testSendMessageEmpty: reject empty text
- testGetMessageHistory: fetch last 50 messages, ordered DESC
- testGetMessageHistoryWithCursor: fetch messages before cursor
- testGetMessageHistoryPagination: multiple calls with cursor
```

**RoomMemberServiceTest**
```
- testIsMember: check membership
- testAddMember: add to room_members
- testRemoveMember: remove from room_members
- testGetMembers: list room members
```

### Backend Integration Tests

**ChatRoomControllerTest**
```
- testCreateRoom: POST /api/rooms (authenticated)
- testListPublicRooms: GET /api/rooms (paginated)
- testJoinRoom: POST /api/rooms/{id}/join
- testLeaveRoom: POST /api/rooms/{id}/leave
- testUnauthorized: requests without auth token should fail
- testRoomNotFound: 404 for non-existent room
```

**MessageControllerTest**
```
- testGetMessageHistory: GET /api/rooms/{id}/messages?limit=50
- testGetMessageHistoryWithCursor: GET with before parameter
- testSendMessage: POST /api/rooms/{id}/messages
- testUserNotMember: 403 if user not in room_members
- testMessageValidation: reject empty/oversized messages
```

**WebSocketHandlerTest**
```
- testSubscribeToRoomTopic: user subscribes to /topic/room/{id}
- testSendMessageBroadcast: message sent to /app/rooms/{id}/message broadcasts to /topic/room/{id}
- testMultipleSubscribers: message receives by all subscribers
- testUserNotMemberCannotSubscribe: unauthorized subscription blocked
```

### Frontend Component Tests

**RoomListPage.test.tsx**
```
- testRenderRoomList: display public rooms
- testSearchRooms: search functionality
- testCreateRoomButton: open create modal
- testJoinRoom: click join, call API
- testNavigateToChat: click room navigates to ChatPage
```

**ChatPage.test.tsx**
```
- testLoadRoom: fetch room on mount
- testLoadMessageHistory: fetch initial messages
- testWebSocketSubscribe: subscribe to room topic
- testSendMessage: send message via WebSocket
- testReceiveMessage: new messages appear in real-time
- testLeaveRoom: unsubscribe and leave
```

**MessageList.test.tsx**
```
- testRenderMessages: display messages in chronological order
- testInfiniteScroll: load older messages when scrolled to top
- testAutoScroll: scroll to bottom on new message
- testNoForceScroll: don't auto-scroll if user scrolled up
```

**useRoomMessages.test.tsx**
```
- testLoadInitialMessages: fetch last 50 messages
- testLoadMoreMessages: cursor-based pagination
- testAddMessage: append new message in real-time
- testMultiplePages: load across multiple pages
```

### Integration Test

**Feature2Integration.test.ts**
```
End-to-end flow:
1. User A creates room "test-room"
2. User A joins room, loads message history
3. User B joins room
4. User A sends message "hello"
5. User B receives message in real-time
6. User B loads history, sees message
7. User B sends message "hi"
8. User A receives in real-time
9. Both users leave room
```

---

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Message delivery latency | <3 seconds | Via WebSocket |
| Room creation | <500ms | REST API |
| Message history load | <800ms | 50 messages |
| Virtual scroll render | 60 FPS | 1000+ messages |
| Room list load | <500ms | 20 rooms per page |
| Presence update | <2 sec | Via WebSocket |

---

## Constraints & Assumptions

**Constraints:**
- Message text max size: 3 KB (enforced backend and frontend)
- Room names must be unique
- Room names immutable after creation
- Only public rooms in Feature #2
- No message editing/deletion in Feature #2
- No attachments/files in Feature #2
- No message replies/threading in Feature #2

**Assumptions:**
- Users authenticated (from Feature #1)
- WebSocket connection stable (graceful degradation deferred)
- Database connection always available
- No network failures during message send (retry logic deferred)
- STOMP/SockJS library handles reconnection basics

---

## Out of Scope (Deferred to Later Features)

- Private rooms (Feature #6)
- Room invitations (Feature #6)
- Message editing/deletion (Feature #7+)
- Message replies/threading (Feature #7+)
- File/image attachments (Feature #8)
- Admin/moderation (ban, remove, delete) (Feature #7)
- Unread message indicators (Feature #3+)
- Message search (Feature #7+)
- Message reactions/emoji (Feature #7+)
- Pinned messages (Feature #7+)

---

## Success Criteria

- ✅ All backend unit tests pass
- ✅ All backend integration tests pass
- ✅ All frontend component tests pass
- ✅ End-to-end integration test passes
- ✅ Two users can create rooms, join, exchange messages in real-time
- ✅ Message history loads correctly with infinite scroll
- ✅ Room name uniqueness enforced
- ✅ Cursor-based pagination works correctly
- ✅ WebSocket delivery latency <3 seconds
- ✅ Database migrations run successfully
- ✅ All code committed with clear commit messages

---

## Next Steps (After Design Approval)

1. Invoke `superpowers:writing-plans` to create implementation plan
2. Create Flyway migration for new tables
3. Implement backend entities, repositories, services (TDD)
4. Implement REST controllers (TDD)
5. Implement WebSocket handler
6. Implement frontend services and hooks (TDD)
7. Implement frontend components (TDD)
8. Run all tests, verify green
9. Manual end-to-end testing
10. Commit feature
11. Proceed to Feature #3
