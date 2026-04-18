# Feature #5 (content): Message Content Enhancements — Design

**Status:** approved 2026-04-18
**Execution slot:** #6 (the App Shell Refactor took #5 per `docs/superpowers/analysis/2026-04-18-wireframes-gap.md`).
**Scope:** Reply / quote, author edit with visible indicator, author soft-delete. Applies to both room messages (`Message`) and direct messages (`DirectMessage`).

## Goal

Bring chat messages in line with the requirements PDF's message features: reply to a message, edit your own message (with "edited" indicator), and delete your own message. Multi-line and Unicode emoji already work through the existing textarea composer.

## Non-goals

| Out of scope | Reason | Where it might land later |
|---|---|---|
| Emoji picker UI | Native OS picker covers Unicode emoji today | Future polish |
| File / image attachments | Separate feature | Feature #6 |
| Threaded sub-conversations | Complexity + timeline; reply is a flat reference | Future |
| Edit time window | Simplicity | Future |
| Admin override on message edit/delete | Explicit scope decision — author-only for this feature | Future |
| Edit history / audit log UI | Original text retained in DB, no UI surface | Future |
| Reactions, pinning, mentions, typing indicators | YAGNI for hackathon | Future |

## Schema — V5 Flyway migration

One migration adds four columns each to `messages` and `direct_messages`:

```sql
ALTER TABLE messages
  ADD COLUMN edited_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_by UUID NULL REFERENCES users(id),
  ADD COLUMN reply_to_id UUID NULL REFERENCES messages(id) ON DELETE SET NULL;

ALTER TABLE direct_messages
  ADD COLUMN edited_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_by UUID NULL REFERENCES users(id),
  ADD COLUMN reply_to_id UUID NULL REFERENCES direct_messages(id) ON DELETE SET NULL;

CREATE INDEX idx_messages_reply_to ON messages(reply_to_id);
CREATE INDEX idx_direct_messages_reply_to ON direct_messages(reply_to_id);
```

Rationale:
- **Soft delete with tombstone:** `deleted_at` set, `text` preserved in DB for audit but blanked in REST/WS responses. This keeps replies to deleted messages resolvable.
- **`ON DELETE SET NULL` on `reply_to_id`:** when a message is hard-deleted by cascades (e.g., owner deletes the room in Feature #4, cascading to all its messages), downstream replies stay but lose the FK. Not relevant at the reply-to-deleted-parent case because that path is soft-delete — the parent row still exists.
- **`deleted_by`:** today equals the author (author-only delete). Makes future admin-delete in rooms a zero-schema change.
- **No new tables:** reply is just an FK, edits mutate in place.

## REST API

### Room messages (extends `MessageController`)

| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/api/rooms/{roomId}/messages` | `{ "text": "...", "replyToId": "uuid\|null" }` | 200 + full DTO (existing endpoint, new optional field) |
| PATCH | `/api/rooms/{roomId}/messages/{id}` | `{ "text": "..." }` | 200 + full DTO |
| DELETE | `/api/rooms/{roomId}/messages/{id}` | — | 204 |

**Auth rules:**
- PATCH: caller must be the message's `userId` (403 otherwise); `deleted_at` must be null (400 otherwise); sets `edited_at = now()`.
- DELETE: caller must be the message's `userId` (403 otherwise); idempotent — deleting an already-deleted message returns 204 and does not update timestamps.
- POST `replyToId`: if provided, must point to a non-deleted-parent (allow deleted-parent too — the preview will show "[deleted]") message in the same room (400 otherwise).

### Direct messages (extends `DirectMessageController`)

Mirrors the above shapes against `/api/dms/{conversationId}/messages/...`. Same auth rules; no admin variance (DMs have no admin concept).

### Response DTO — both `Message` and `DirectMessage`

```json
{
  "id": "uuid",
  "userId": "uuid",
  "username": "string",
  "text": "string | null",
  "createdAt": "2026-04-18T12:34:56Z",
  "editedAt": "2026-04-18T12:35:00Z | null",
  "deletedAt": "2026-04-18T12:40:00Z | null",
  "deletedBy": "uuid | null",
  "replyTo": {
    "id": "uuid",
    "authorUsername": "string",
    "textPreview": "string"
  } | null
}
```

Semantics:
- When `deletedAt` is non-null, `text` is `null` in the response (DB value retained for audit).
- `editedAt` non-null → frontend shows `(edited)` suffix.
- `replyTo.textPreview` is the first 100 characters of the parent's `text`; if the parent is deleted, value is the literal string `"[deleted]"`.
- `replyTo` stays null for non-reply messages; field always present in the JSON envelope for consistent shape.
- For room-message responses, `username` continues to come from the `users` table join (same as today's `MessageController` history endpoint).

### History endpoints

Existing history endpoints (`GET /api/rooms/{roomId}/messages`, `GET /api/dms/{conversationId}/messages`) return the new DTO shape. Deleted rows are still returned (so replies still resolve on the client) with `text: null`, `deletedAt` set.

## WebSocket events

### Envelope — tagged union

Existing destinations stay the same:
- Rooms: `/topic/rooms/{roomId}`
- DMs: per-user queue (`SimpMessagingTemplate.convertAndSendToUser(username, "/queue/dms", payload)`)

Payload gains a top-level `type`:

```json
{ "type": "CREATED", "message": { ...full DTO (as above)... } }
{ "type": "EDITED",  "message": { ...full DTO with editedAt set... } }
{ "type": "DELETED", "message": { "id": "uuid", "deletedAt": "ISO", "deletedBy": "uuid" } }
```

Client handler dispatches:
- `CREATED` → append
- `EDITED` → upsert by `id`
- `DELETED` → in-place tombstone (set `deletedAt`, null `text`)

**Backwards compat:** server + frontend change together in the same feature. The envelope is breaking — today's payload is the DTO directly, no `type` field — but no external client consumes these channels. Both sides land in lockstep (plan tasks order them so each commit compiles + runs).

## Frontend UX

### Message row (shared `MessageItem` component extracted from `MessageList`)

- **Normal message:** timestamp, username, body.
- **Edited message:** `(edited)` in muted text after the timestamp.
- **Deleted message:** italic muted `Message deleted` in place of body; no reply header; no hover actions.
- **Reply header (when `replyTo` != null):** small border-left quote bar above the body showing `@{authorUsername}` and `{textPreview}`. Clicking scrolls the referenced message into view (no highlight pulse).
- **Hover actions** (floating top-right of the row): `↩ Reply` (always) · `✎ Edit` (author only) · `🗑 Delete` (author only). Hidden when the row is in edit mode or is a tombstone.

### Composer — reply pill in existing `ComposerActions` slot

- Clicking `Reply` sets a local `replyTarget` state in the page (`ChatPage` / `DirectChatPage`).
- `ReplyPill` renders inside `ComposerActions`: `Replying to @alice: "first 100 chars..."  ×`.
- `×` dismisses.
- Sending a message while the pill is active attaches `replyToId` to the send call and clears the pill.

### Inline edit — `InlineMessageEditor`

- Clicking `✎ Edit` flips the row to edit mode: a textarea prefilled with the current text + `Save` / `Cancel` buttons.
- `Ctrl+Enter` saves; `Esc` cancels.
- On save → PATCH → server responds with the updated DTO → local list upserts; server ALSO emits `EDITED` WS for other clients.

### Delete confirmation

- Native `window.confirm("Delete this message?")`. DELETE on confirm.
- No custom modal (hackathon scope).

## Components

### New

| Component | Purpose |
|---|---|
| `MessageItem` | Single message row; extracts current inline render from `MessageList`. Owns hover actions, reply header, tombstone render, edit-mode flip. |
| `MessageActionsMenu` | Floating hover action bar; receives `isAuthor`, `onReply`, `onEdit`, `onDelete` callbacks. |
| `ReplyPill` | Reply-in-progress chip inside `ComposerActions`; receives `replyTarget` and `onDismiss`. |
| `InlineMessageEditor` | Small inline textarea + Save/Cancel; receives `initialText`, `onSave`, `onCancel`. |

### Modified

| Component | Change |
|---|---|
| `MessageList` | Render `<MessageItem>` per item instead of inlining the JSX; forward action callbacks. |
| `MessageInput` | No interface change — `actions` prop was introduced in the shell refactor. `ChatPage` / `DirectChatPage` pass a `<ReplyPill>` when reply is active. |
| `ChatPage` / `DirectChatPage` | Add `replyTarget` state; handle edit/delete callbacks from `MessageList`; subscribe handler switches on `type`. |
| `useRoomMessages` / `useDirectMessages` | Add `upsertMessage(id, patch)` and `markDeleted(id, meta)` helpers; their event handlers branch on payload `type`. |
| `messageService` / `directMessageService` | Add `editMessage`, `deleteMessage`; update `sendMessage` signature to accept optional `replyToId`. |
| `types/room.ts` / `types/directMessage.ts` | `Message`/`DirectMessage` gain `editedAt`, `deletedAt`, `deletedBy`, `replyTo`. |

## Data flow — reply send (example)

1. Alice clicks `↩ Reply` on Bob's message `M`.
2. `ChatPage.setReplyTarget(M)` — pill renders in composer.
3. Alice types + sends. `MessageInput.onSend('hello')` — `ChatPage` calls either REST POST (the current room flow sends via WS, so if we keep WS send, a new outbound payload shape including `replyToId` is needed; otherwise switch this path to REST to simplify). **Decision:** keep the existing WS-based send for rooms — add `replyToId` to the outbound STOMP payload on `/app/rooms/{roomId}/messages`. Server-side handler passes it to `MessageService.createMessage(roomId, userId, text, replyToId)`. Same for DMs.
4. Server persists, emits `CREATED` with the full DTO including `replyTo`.
5. Alice's client + Bob's client receive — `MessageList` renders with the quote header.
6. `ChatPage.replyTarget` resets to null on successful echo.

## Error handling

- Edit deleted message → 400 `{"error":"Cannot edit a deleted message"}`. Frontend shows inline alert next to editor.
- Edit/delete another user's message → 403. Frontend guard keeps the UI from offering this, but the backend is authoritative.
- Reply to a message in a different room → 400. Not reachable through the UI but backend validates.
- WS `DELETED` arrives for an unknown `id` (e.g., this client never loaded that page of history) → ignore silently.

## Tests

| Level | Coverage |
|---|---|
| Backend unit (`MessageServiceTest` / `DirectMessageServiceTest`) | author-only edit (403 path); edit on soft-deleted → rejected; delete idempotent; reply cross-room / cross-conversation → rejected; reply to deleted parent → allowed; history returns tombstones with null text. |
| WS emission | After each mutation, `SimpMessagingTemplate` sends the correct tagged envelope to the correct destination. |
| Backend controller (MockMvc) | 403 non-author edit + delete; 400 deleted-message edit; 400 bad `replyToId`; 204 delete (fresh + idempotent); 200 edit. Same two controllers (`MessageController`, `DirectMessageController`). |
| Backend integration (`MessageContentFlowIntegrationTest`) | End-to-end: send → reply → edit → delete for both rooms and DMs; history returns the full four rows with tombstone + reply resolution. |
| Frontend unit (vitest) | `MessageItem` tombstone / edited / reply header. `ReplyPill` dismisses. `InlineMessageEditor` Ctrl+Enter / Esc. `useRoomMessages` handler upserts on `EDITED` / tombstones on `DELETED`. |
| Frontend E2E (`message-content.spec.ts`) | Two-browser scenario: Alice sends, Bob replies (both see reply header), Alice edits (Bob sees "(edited)"), Alice deletes (Bob sees tombstone). |

## Risk

- **Deadline**: ~36 h remaining at design time. Plan must split into small commits that each compile and pass tests on their own.
- **WS envelope breaking change**: rooms + DMs each need server + client landed together. Order the plan so one side's commit doesn't break the other.
- **Two entities with parallel logic**: minimize duplication by naming patterns (mirror methods) — but do not introduce a shared base class that would touch `Message` + `DirectMessage` at once. The refactoring tax isn't worth it for two callers.

## File-path summary

```
backend/src/main/resources/db/migration/V5__message_content.sql

backend/src/main/java/com/hackathon/features/messages/
  Message.java                        (+editedAt, deletedAt, deletedBy, replyToId)
  MessageService.java                 (+editMessage, +deleteMessage; createMessage(+replyToId))
  MessageController.java              (+PATCH, +DELETE; body +replyToId)
  MessageEventEnvelope.java           (new — tagged union record)

backend/src/main/java/com/hackathon/features/dms/
  DirectMessage.java                  (same +columns)
  DirectMessageService.java           (same +methods)
  DirectMessageController.java        (same)
  DirectMessageEventEnvelope.java     (new — tagged union record)

backend/src/test/java/com/hackathon/features/messages/
  MessageServiceTest.java             (extend)
  MessageControllerTest.java          (extend)

backend/src/test/java/com/hackathon/features/dms/
  DirectMessageServiceTest.java       (extend)
  DirectMessageControllerTest.java    (extend)

backend/src/test/java/com/hackathon/features/integration/
  MessageContentFlowIntegrationTest.java  (new)

frontend/src/types/
  room.ts                             (Message +editedAt, deletedAt, deletedBy, replyTo)
  directMessage.ts                    (same)

frontend/src/services/
  messageService.ts                   (+editMessage, +deleteMessage; sendMessage(+replyToId))
  directMessageService.ts             (same)

frontend/src/hooks/
  useRoomMessages.ts                  (branch on event type; upsert / markDeleted helpers)
  useDirectMessages.ts                (same)
  useWebSocket.ts                     (send with replyToId parameter)
  useDirectMessageSocket.ts           (same)

frontend/src/components/
  MessageItem.tsx                     (new — extracted from MessageList)
  MessageActionsMenu.tsx              (new)
  ReplyPill.tsx                       (new)
  InlineMessageEditor.tsx             (new)
  MessageList.tsx                     (renders <MessageItem> per row; passes callbacks through)

frontend/src/pages/
  ChatPage.tsx                        (replyTarget state; edit/delete handlers)
  DirectChatPage.tsx                  (same)

frontend/e2e/
  message-content.spec.ts             (new)
```

## Verification checklist

- [ ] `./gradlew test` green (new + existing)
- [ ] `npm run build` clean
- [ ] `npm test -- --run` green
- [ ] `npm run test:e2e` green (including `message-content.spec.ts`)
- [ ] Browser smoke: send, reply with quote header, edit shows `(edited)`, delete shows tombstone — for one room and one DM
- [ ] `FEATURES_ROADMAP.md` updated — Feature #5 (content) marked ✅ complete
