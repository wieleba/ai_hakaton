# Server-Side Embed Metadata — Design

**Date:** 2026-04-20
**Feature:** #11 (split out of #10 — YouTube link embeds)
**Status:** Approved, pending implementation plan

## Why

The current YouTube inline player is frontend-only: every client re-runs
`extractYouTubeIds.ts` regex on every render of every message. This works but
has two limits:

1. No server-side visibility into which messages carry embeds → future "ban
   this video across rooms" moderation is impossible without a scan pass.
2. No cached `title` / `thumbnail_url` → the iframe shows "Loading…" for a beat
   before YouTube's own thumbnail renders. A server-side cache gives us richer,
   faster previews.

Persisting embeds on send also centralises future extensions (Twitter/X,
Spotify, generic OpenGraph) without forcing every client to ship a matching
regex.

## Scope (Approved)

- **Extract on send** — YouTube URL detection in Java, mirror of the frontend
  regex. Persist `{kind, canonical_id, source_url}` for every match.
- **YouTube oEmbed** — synchronous call to
  `https://www.youtube.com/oembed?url=...&format=json` with a 1.5 s timeout,
  persist the returned `title` + `thumbnail_url`. Any failure (timeout, non-2xx,
  parse error) → persist the row with nulls; never fail the send.
- **Backfill existing messages** on migration.
- **Frontend** reads DTO-provided embeds first; falls back to the existing
  regex when `embeds` is empty (safety net for unbackfilled or extraction-failed
  rows).

Out of scope for this feature: generic OpenGraph scraping, Twitter/X, Spotify,
async enrichment, per-embed click analytics, cross-room embed search/moderation
UI (schema supports it; UI comes later).

## Schema (V11 migration)

Two new tables, mirroring the `message_reactions` / `direct_message_reactions`
pattern:

```sql
CREATE TABLE message_embeds (
    id             UUID        PRIMARY KEY,
    message_id     UUID        NOT NULL
                               REFERENCES messages(id) ON DELETE CASCADE,
    kind           VARCHAR(16) NOT NULL,      -- 'youtube' today
    canonical_id   VARCHAR(64) NOT NULL,      -- e.g. 'dQw4w9WgXcQ'
    source_url     TEXT        NOT NULL,
    title          TEXT        NULL,
    thumbnail_url  TEXT        NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (message_id, canonical_id)
);
CREATE INDEX idx_message_embeds_canonical ON message_embeds (canonical_id);

CREATE TABLE direct_message_embeds (
    id                 UUID        PRIMARY KEY,
    direct_message_id  UUID        NOT NULL
                                   REFERENCES direct_messages(id) ON DELETE CASCADE,
    kind               VARCHAR(16) NOT NULL,
    canonical_id       VARCHAR(64) NOT NULL,
    source_url         TEXT        NOT NULL,
    title              TEXT        NULL,
    thumbnail_url      TEXT        NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (direct_message_id, canonical_id)
);
CREATE INDEX idx_dm_embeds_canonical ON direct_message_embeds (canonical_id);
```

- **CASCADE** — message hard-delete (rare: only on room/conversation delete)
  takes its embeds. Message soft-delete keeps the embed rows so the moderation
  trail is preserved.
- **UNIQUE (message_id, canonical_id)** — a single message can reference the
  same video twice; we store it once. Edits that keep the URL don't duplicate.
- **`kind` as VARCHAR** — keeps the door open for `twitter` / `spotify` /
  `og` without a schema change.

## Java Components

All under `features/messages/embeds/` (a new package; keeps existing
`features/messages/` file listing stable).

### `YouTubeUrlExtractor` — stateless utility

```java
public record Extracted(String kind, String canonicalId, String sourceUrl) {}

public static List<Extracted> extract(String text) { ... }
```

Mirrors `frontend/src/utils/extractYouTubeIds.ts`: handles `youtube.com/watch`,
`youtu.be/<id>`, `youtube.com/shorts/<id>`, `youtube.com/embed/<id>`, optional
query params. Dedupes by `canonicalId`, preserves encounter order.

No Spring dependency — pure function, trivially unit-testable.

### `YouTubeOEmbedClient` — Spring bean

```java
record OEmbedData(String title, String thumbnailUrl) {}

Optional<OEmbedData> fetch(String sourceUrl);
```

Uses Spring 6 `RestClient` with a 1.5 s connect + read timeout.
`https://www.youtube.com/oembed?url=<urlencode(sourceUrl)>&format=json`.

Failure handling: any exception (timeout, `HttpClientErrorException`,
`HttpServerErrorException`, JSON parse error, malformed response) returns
`Optional.empty()`. No retries — the cost of blocking message send outweighs
the marginal benefit of one retry.

### `EmbedService` — Spring bean

```java
void persistForMessage(Message msg);       // called from MessageService.createMessage
void persistForDirectMessage(DirectMessage dm);
void reconcileForMessage(Message msg);     // called on message edit
void reconcileForDirectMessage(DirectMessage dm);
```

`persistFor*`:
1. `extracted = YouTubeUrlExtractor.extract(msg.text)`
2. For each hit: `oEmbedClient.fetch(hit.sourceUrl())` (best-effort)
3. `INSERT ... ON CONFLICT (message_id, canonical_id) DO NOTHING`

`reconcileFor*` (edit path):
1. Extract from new text
2. `DELETE FROM message_embeds WHERE message_id = ? AND canonical_id NOT IN (...)`
3. Insert new hits (same ON CONFLICT semantics)

Called *after* the message transaction commits so the FK resolves.
Implementation option: `@TransactionalEventListener(AFTER_COMMIT)` on a
`MessageCreatedEvent` / `MessageEditedEvent` bus, to keep `MessageService`
ignorant of embeds.

### DTOs

```java
public record EmbedDto(
    UUID id,
    String kind,
    String canonicalId,
    String sourceUrl,
    String title,          // nullable
    String thumbnailUrl    // nullable
) {}
```

`MessageDto` and `DirectMessageDto` gain `List<EmbedDto> embeds` (empty list if
none). Included in:
- `GET /api/rooms/{id}/messages` paginated history
- `GET /api/dms/{id}/messages` paginated history
- The existing WebSocket `CREATED` / `EDITED` envelopes (both `/topic/room/{id}`
  and `/user/{uuid}/queue/dms`)

## Backfill (V12 Java migration)

`V12__backfill_embeds.java` — Flyway Java migration, runs once on next startup.

```
log.info("Backfill V12 starting")
roomCount = iterate(messages, row -> {
    persistEmbedsFor(row)  // same logic as EmbedService but via JdbcTemplate
    Thread.sleep(200)      // ~5 req/s — stay under oEmbed informal limits
})
dmCount = iterate(direct_messages, ...)
log.info("Backfill V12 done: {} room + {} DM embeds", roomCount, dmCount)
```

Hard rule: **the migration never fails.** A per-row oEmbed failure is caught,
the embed row is persisted with null metadata, migration continues. The only
way V12 fails is a schema/DB issue, which is a real problem anyway.

The migration uses `JdbcTemplate` and duplicates the extract/persist logic
rather than depending on the service layer, because Flyway migrations run
before Spring's context is fully refreshed. Duplication is small (~30 lines)
and isolated.

## Frontend

### DTO type change

`types/room.ts` and `types/directMessage.ts`:

```ts
export interface Embed {
  id: string;
  kind: 'youtube' | string;
  canonicalId: string;
  sourceUrl: string;
  title: string | null;
  thumbnailUrl: string | null;
}

export interface Message {
  // ...existing fields
  embeds: Embed[];     // always present; empty array if none
}
```

### Rendering

`MessageItem.tsx` passes `message.embeds` to a reshaped `YouTubeEmbed`:

```tsx
<YouTubeEmbed text={message.text} embeds={message.embeds} />
```

Inside `YouTubeEmbed`:

```
if (embeds?.filter(e => e.kind === 'youtube').length > 0) {
  render DTO-provided (canonicalId → iframe, title/thumbnail as caption)
} else {
  render from extractYouTubeIds(text)   // fallback for historical / unbackfilled
}
```

The fallback branch is the current code unchanged. This guarantees
zero-regression even before V12 finishes backfilling on first start.

## Tests

### Backend unit
- `YouTubeUrlExtractorTest` — URL matrix identical to
  `frontend/src/utils/__tests__/youtube.test.ts` (9 cases): watch,
  youtu.be, shorts, embed, multiple-per-message, extra params, dedupe,
  non-matches, mixed text.
- `YouTubeOEmbedClientTest` — MockWebServer: 200 happy path, 404, 429, timeout,
  malformed JSON — each returns `Optional.empty()` or the expected data.
- `EmbedServiceTest` — persist dedupe on unique; graceful on oEmbed
  `Optional.empty()` (row still persisted with nulls); edit-then-remove
  deletes stale rows.

### Backend integration
- `EmbedFlowIntegrationTest` — POST a message with a YT URL, read back via GET,
  assert DTO carries embed with populated title/thumbnail (MockWebServer for
  the oEmbed endpoint). Same flow for DMs.

### Migration
- `V12BackfillMigrationTest` — seed 3 messages with YT URLs pre-V12, run
  Flyway against a testcontainer Postgres, assert rows in `message_embeds`.
  Uses MockWebServer for oEmbed.

### Frontend
- One new Vitest case: DTO-provided embed renders with title + thumbnail;
  missing embed falls back to regex.

### E2E
- Extend `chat-layout.spec.ts`: send a YouTube URL, assert the iframe renders
  (title/thumbnail optional — they depend on YouTube's live response).

## Testing-the-tests note

MockWebServer is required because hitting real `youtube.com/oembed` from CI
is flaky. The production code reads the oEmbed base URL from configuration
(`app.oembed.youtube-url`, default `https://www.youtube.com/oembed`) so tests
point at `http://localhost:<port>/oembed`.

## Rollout

1. V11 schema migration (table only, no code using it yet)
2. `EmbedService` + `YouTubeUrlExtractor` + `YouTubeOEmbedClient` + wiring
   into `MessageService.createMessage` and edit
3. DTO expansion + frontend consumer
4. V12 backfill migration
5. Playwright verification

Each step independently green: after step 1 the DB has empty tables, after
step 2 new messages get embeds, after step 3 frontend renders them, after
step 4 historical messages also have them.

## Open questions / non-goals

- **Rate-limit observability.** If oEmbed starts 429-ing in prod, we'd want a
  counter. Ship without; add a `Micrometer` gauge if it becomes a problem.
- **Invalidation.** YouTube titles can change. Today's design caches forever.
  A future `re-fetch oEmbed data older than 30d` job is trivial to add but
  out of scope.
- **Non-YouTube embeds.** Schema is generic, but Twitter/X and OpenGraph need
  their own extractor + client beans. Not this feature.
