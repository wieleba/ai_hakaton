# Emoji Picker + Message Reactions — Design

**Status:** approved 2026-04-19
**Context:** ships as a follow-up to Feature #5 content (reply + edit + delete).

## Goal

1. **Emoji picker in composer** — a button in the existing `ComposerActions` slot opens a grid; clicking an emoji inserts it at the cursor in the textarea.
2. **Message reactions** — users click a quick-reaction (or open the picker from the hover menu) to toggle an emoji reaction on any message. Reactions show as clickable chips under the message body; clicking toggles your own.

Applies to both room messages and direct messages.

## Non-goals

| Out of scope | Why |
|---|---|
| Custom emoji (uploaded) | Unicode only |
| Reaction notifications | Feature #7/#8 scope |
| Emoji skin-tone picker persistence | Library default |
| Reaction-author list on hover ("who reacted") | Deferrable polish |
| Admin-override removal of other users' reactions | Not needed for hackathon |

## Schema — V6 migration

```sql
CREATE TABLE message_reactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (message_id, user_id, emoji)
);
CREATE INDEX idx_message_reactions_message ON message_reactions(message_id);

CREATE TABLE direct_message_reactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  direct_message_id UUID NOT NULL REFERENCES direct_messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (direct_message_id, user_id, emoji)
);
CREATE INDEX idx_direct_message_reactions_message ON direct_message_reactions(direct_message_id);
```

## REST API

```
POST   /api/rooms/{roomId}/messages/{id}/reactions   body: {emoji}  → 200 (toggle: add or remove)
GET    (not needed — reactions live on the message DTO)

POST   /api/dms/{cid}/messages/{id}/reactions        body: {emoji}  → 200
```

Toggle semantics: if the caller already has that `(message_id, user_id, emoji)` row, DELETE it; otherwise INSERT. Returns the updated message DTO (including reactions).

## DTO changes

Both `ChatMessageDTO` and `DirectMessageDTO` gain:

```java
private List<ReactionSummary> reactions;
```

```java
public record ReactionSummary(String emoji, int count, boolean reactedByMe) {}
```

Populated in service `toDto(msg, callerId)` — note the new `callerId` overload so `reactedByMe` can be determined. The old `toDto(msg)` stays as `toDto(msg, null)` for WS broadcasts where we don't know the recipient (the frontend can reconcile `reactedByMe` itself by checking if its own user appears in a companion list — OR we simply broadcast `reactedByMe=false` and refresh the flag client-side based on the event being from us).

**Simplification:** WS broadcasts use `reactedByMe=false` for everyone. Clients locally flip the flag for their own user when they initiate a toggle (optimistic). REST responses populate `reactedByMe` correctly for the caller. Good enough.

## WebSocket propagation

When a reaction toggles, the service emits an `EDITED` envelope with the full message DTO (including updated reactions). Frontend `upsertMessage` already handles it.

## Frontend

### Emoji picker in composer

- New component `EmojiPickerButton` renders in `ComposerActions`. Button shows `😀`. Clicking opens a floating `emoji-picker-react` panel.
- Passes an `onPick(emoji: string)` callback up to the page. Pages (`ChatPage` / `DirectChatPage`) receive the emoji and forward to `MessageInput` via a new `onPickEmoji` prop; `MessageInput` inserts at current textarea cursor position.

### Message reactions UI

- `ReactionsBar` component renders below the message body. Each chip: `[emoji count]`, click toggles. Highlight when `reactedByMe`.
- Hover actions menu adds a `😀+` button that opens `emoji-picker-react` scoped to that message; picking adds a reaction.

### Data flow

- `messageService.toggleReaction(roomId, messageId, emoji)` → POST. Returns updated DTO.
- `directMessageService.toggleReaction(conversationId, messageId, emoji)` → POST.
- Hooks already have `upsertMessage`; WS `EDITED` event updates the list in place.

## Tests

| Level | Coverage |
|---|---|
| Backend unit | Toggle add / remove for rooms + DMs; author + non-author can both react; different emoji by same user allowed; UNIQUE constraint enforced |
| Backend controller | 200 response; 401 without JWT |
| Frontend unit | `ReactionsBar` renders chips; click toggles; `EmojiPickerButton` opens/closes |
| Frontend E2E | Alice adds reaction to Bob's message; Bob sees it |

## Plan

1. V6 migration + ReactionSummary record + DTO fields
2. Room reaction entity + repository + service.toggle + controller + emit EDITED + tests
3. DM reaction entity + repository + service.toggle + controller + emit EDITED + tests
4. Frontend types + services
5. EmojiPickerButton + insert-at-cursor in MessageInput + wiring in ChatPage/DirectChatPage
6. ReactionsBar + MessageItem integration (chips under body + reaction button in hover menu)
7. FEATURES_ROADMAP.md update
