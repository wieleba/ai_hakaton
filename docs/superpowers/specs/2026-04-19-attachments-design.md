# Feature #6: Attachments (File + Image Sharing) — Design

**Status:** approved 2026-04-19
**Scope:** upload one file per message in both chat rooms and direct messages; store in S3-compatible object storage (MinIO locally, any S3 endpoint in prod); render images inline, non-images as download links.

## Goal

1. Users can attach one file to a chat message or direct message.
2. Images render inline; other allowed files render as download links.
3. Storage is multi-instance safe — object storage (S3 protocol, MinIO in dev) rather than local filesystem.
4. Cascade: deleting a message/room/conversation/account removes the attachment DB row via FK cascade (S3 objects orphan on cascade, cleaned manually by service on explicit soft-delete; full orphan cleanup is future work).

## Non-goals

| Out of scope | Reason |
|---|---|
| Multi-attachment per message | Hackathon scope |
| Image thumbnails / resizing | Serve originals; client-side sizing in CSS |
| Video / audio | Excluded from MIME allow-list |
| Per-room / per-user quotas | 10 MB per-file cap is the only gate |
| Drag-and-drop onto message list | Attach button in composer only |
| Replace-attachment on edit | Delete + resend |
| Orphan S3 cleanup on hard cascade (room/account delete) | Known limitation; a separate cleanup job is future work |

## Storage choice

S3-compatible object storage. **MinIO** runs locally in `docker-compose.yml`; production points the same SDK at AWS S3, Cloudflare R2, DigitalOcean Spaces, etc. Backend always proxies downloads — MinIO credentials never reach the browser.

### `docker-compose.yml` addition

```yaml
minio:
  image: minio/minio
  container_name: chat-minio
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
  ports:
    - "9000:9000"
    - "9001:9001"
  volumes:
    - minio_data:/data
  networks:
    - chat-network
```

New volume `minio_data` added alongside `postgres_data`. Backend service gains `depends_on: minio` (not required for start order but documents the dependency).

### Backend dependency

```gradle
implementation 'software.amazon.awssdk:s3:2.28.17'
```

(Any recent 2.x version is fine.)

### `application.yml`

```yaml
storage:
  s3:
    endpoint: http://minio:9000      # override via STORAGE_S3_ENDPOINT in prod
    region: us-east-1                 # MinIO ignores, S3 SDK requires non-null
    access-key: minioadmin            # override via STORAGE_S3_ACCESS_KEY
    secret-key: minioadmin            # override via STORAGE_S3_SECRET_KEY
    bucket: chat-attachments
    path-style-access: true           # required for MinIO; off for real S3
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
```

## Schema — V8 migration

```sql
CREATE TABLE message_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  filename VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_message_attachments_message ON message_attachments(message_id);
CREATE UNIQUE INDEX uq_message_attachments_message ON message_attachments(message_id);

CREATE TABLE direct_message_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  direct_message_id UUID NOT NULL REFERENCES direct_messages(id) ON DELETE CASCADE,
  filename VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_direct_message_attachments_message ON direct_message_attachments(direct_message_id);
CREATE UNIQUE INDEX uq_direct_message_attachments_message ON direct_message_attachments(direct_message_id);
```

The UNIQUE index enforces the one-attachment-per-message scope choice.

`storage_key` is the S3 object key — a bare UUID (no path prefix needed; flat key space inside the bucket).

## REST API

### Send a message with (or without) an attachment

Room messages — existing text-only JSON endpoint stays; an additional multipart variant lives on the same URL:

```
POST /api/rooms/{roomId}/messages
Content-Type: multipart/form-data
Parts:
  - text: string (optional; at least one of text or file required)
  - file: binary (optional; 10MB max; allow-list MIME required)
  - replyToId: uuid (optional; same-room validation as today)
```

Response: `200 ChatMessageDTO` (includes populated `attachment` when a file was sent).

Errors:
- 400 — neither text nor file; empty text + no file; MIME not allow-listed; zero bytes
- 413 — file larger than 10 MB (Spring multipart error; default mapping)
- 403 — caller not a room member

DM mirror:

```
POST /api/dms/{conversationId}/messages
Content-Type: multipart/form-data
```

Same parts, returns `DirectMessageDTO`.

The existing JSON-bodied `POST` on both URLs continues to accept text-only requests unchanged — Spring dispatches by `Content-Type`.

### Download attachment bytes

```
GET /api/attachments/{attachmentId}/content
```

Response:
- 200 with streamed body
- `Content-Type: <attachment.mime_type>`
- `Content-Length: <size_bytes>`
- `Content-Disposition: inline; filename="<urlencoded>"` for `image/*` allow-listed types
- `Content-Disposition: attachment; filename="<urlencoded>"` for every other MIME

Auth:
- 401 without a valid JWT (existing `SecurityConfig` entry point)
- 403 when the caller is neither a member of the attachment's room nor a participant of its conversation
- 404 when no attachment with that id exists

Lookup: the controller resolves the id against both `message_attachments` and `direct_message_attachments`. Exactly one table contains any given id.

### MIME allow-list

Images (rendered inline):
- `image/png`, `image/jpeg`, `image/gif`, `image/webp`

Documents (rendered as download):
- `application/pdf`, `text/plain`, `application/zip`

Everything else — including `image/svg+xml`, `text/html`, `application/javascript`, and unknown types — rejected with 400. The list is a constant in `AttachmentPolicy` so it's easy to extend later.

## DTO shape

```java
public record AttachmentSummary(
    UUID id,
    String filename,
    String mimeType,
    long sizeBytes) {}
```

Both `ChatMessageDTO` and `DirectMessageDTO` gain:

```java
private AttachmentSummary attachment;   // null when no attachment
```

Populated in `MessageService.toDto` / `DirectMessageService.toDto` via the respective attachment repository (single query per message). When the owning message is soft-deleted, the service nulls this field in the outbound DTO (existing "blank `text` on tombstone" behavior extends here).

## Architecture

### `StorageService` abstraction

```
backend/src/main/java/com/hackathon/shared/storage/
  StorageService.java            (interface)
  S3StorageService.java          (impl; @Primary @Profile("!test-nostorage"))
  StorageConfig.java             (@ConfigurationProperties + @Bean S3Client)
  BucketInitializer.java         (@Component that runs on ApplicationReadyEvent, idempotent)
```

Interface:

```java
public interface StorageService {
  String store(InputStream content, long size, String mimeType);   // returns the storage key
  InputStream load(String storageKey);
  long sizeOf(String storageKey);                                    // head object
  void delete(String storageKey);
}
```

`S3StorageService` uses `S3Client` from the AWS SDK. `store` generates a UUID for the key, PUTs the object. `load` GETs the object and returns the HTTP response body stream (`ResponseInputStream<GetObjectResponse>`). `delete` issues a single DeleteObject call (idempotent — missing key returns success).

### `BucketInitializer`

On `ApplicationReadyEvent`, calls `HeadBucket`. If it returns 404, calls `CreateBucket`. Logs. Makes local dev "just work" without manual MinIO setup.

### `AttachmentPolicy`

Small utility class listing allow-listed MIME types + predicate `isImage(String mime)`. Imported by controllers to set `Content-Disposition` correctly and by services to validate uploads.

### New entities

```
backend/src/main/java/com/hackathon/features/messages/
  MessageAttachment.java             (JPA entity)
  MessageAttachmentRepository.java

backend/src/main/java/com/hackathon/features/dms/
  DirectMessageAttachment.java
  DirectMessageAttachmentRepository.java

backend/src/main/java/com/hackathon/features/attachments/
  AttachmentController.java          (unified /api/attachments/{id}/content)
  AttachmentService.java             (lookup + auth helper; serves both tables)
```

The lookup helper (in `AttachmentService`) returns a small polymorphic record describing which table the id came from plus the parent message id + auth scope (roomId or conversationId), which the controller uses to build 403/404 + stream bytes.

### Service extensions

`MessageService`:
- New overload `sendMessage(roomId, userId, text, replyToId, MultipartFile file)` — validates file, stores via `StorageService`, persists `Message` and `MessageAttachment` in one `@Transactional` method, publishes `CREATED` envelope over WS.
- `deleteMessage` (existing soft-delete path) — look up any attachment row, delete the S3 object via `StorageService.delete`, then delete the attachment row; continue with existing soft-delete of message.
- `toDto(Message m, UUID callerId)` gains a repository lookup + `AttachmentSummary` build.

`DirectMessageService`:
- Same shape, operating on `DirectMessageAttachmentRepository`.

### Controller wiring

`MessageController` + `DirectMessageController` get an additional `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` handler. Existing JSON handlers stay untouched.

`AttachmentController` is new:

```java
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {
  @GetMapping("/{id}/content") ...
}
```

## Frontend

### `frontend/src/types/attachment.ts`

```typescript
export interface AttachmentSummary {
  id: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
}

export const isImageMime = (m?: string | null) =>
  !!m && /^image\/(png|jpeg|gif|webp)$/.test(m);
```

### `frontend/src/services/messageService.ts`

Add `sendMessageWithAttachment(roomId, text, file, replyToId?)` — multipart POST. Keeps existing `sendMessage` unchanged.

### `frontend/src/services/directMessageService.ts`

Same addition for DMs.

### Composer UI

Add an attach button (paperclip icon, clickable) inside `ComposerActions` next to the existing emoji button. Clicking opens a hidden `<input type="file">`. Selected file shows a removable preview chip above the textarea (filename + size + ×). Send-path:

- If a file is staged → REST multipart (send WITH attachment)
- No file + non-empty text → existing WS path (unchanged)
- No file + empty text → send button stays disabled

After a successful REST send, the composer clears both the text and the staged file. The server's broadcast over WS delivers the message to all clients (including the sender — the existing hook already deduplicates by id).

### Rendering in `MessageItem`

When `message.attachment` is present:
- If `isImageMime(attachment.mimeType)` → render `<img src="/api/attachments/{id}/content" alt={filename}>` with `max-height: 320px`, `max-width: 100%`, `rounded`, click-to-open-in-new-tab
- Else → render a download anchor `<a href="/api/attachments/{id}/content" download={filename}>{filename} ({prettySize})</a>`

Adapter note: `DirectChatPage` already maps `DirectMessage` → the shared `Message` shape — add the `attachment` field to that adapter.

## Tests

| Level | Coverage |
|---|---|
| Backend unit — `AttachmentServiceTest` | MIME allow-list accepts listed types and rejects SVG / HTML / application/octet-stream. Oversize rejected. |
| Backend unit — `S3StorageServiceTest` | round-trip via MinIO testcontainer: store(stream, size, mime) → load(key) returns identical bytes; delete(key) makes subsequent load fail; delete on unknown key is a no-op |
| Backend service — `MessageServiceTest` | upload-attached message sets the attachment row correctly; soft-delete unlinks the S3 object; hard-delete of message cascades row removal; attachment survives across a `toDto` roundtrip |
| Backend controller (MockMvc) | multipart POST with image part returns DTO with attachment; POST with no text and no file → 400; POST with oversized payload → 413; text-only JSON POST still works |
| Backend controller — `AttachmentController` | 200 streams bytes + correct headers for both images (inline) and documents (attachment); 403 for non-members; 404 for unknown id; 401 without JWT |
| Backend integration — `AttachmentFlowIntegrationTest` | Alice uploads a PNG in a room, Bob downloads (bytes match); Alice uploads in a DM, Bob downloads; Alice soft-deletes → Bob's next download returns 404; Alice hard-deletes room → attachment rows gone |
| Frontend unit (vitest) | `MessageItem` renders `<img>` for image mime and `<a download>` otherwise; composer attach chip appears + removes; "send" posts multipart when file is staged |
| Frontend E2E — `attachments.spec.ts` | Alice uploads a tiny PNG in a room, Bob sees the image; Alice uploads a small PDF in a DM, Bob clicks the download link (asserting the anchor has the right `href` and `download` attribute); over-10MB upload surfaces an error to the sender |

The `S3StorageServiceTest` uses a `MinIOContainer` from testcontainers (there's a community image, or use the generic `GenericContainer<>("minio/minio")` pattern — picked at plan time). If testcontainers-minio proves finicky, we'll fall back to a `@Profile("test") InMemoryStorageService` that stores bytes in a map and `S3StorageServiceTest` becomes a simple unit test of the stub — noted as a plan-level fallback.

## Security notes

- Filenames echoed back in `Content-Disposition` are RFC-5987 percent-encoded to prevent header injection.
- `image/svg+xml` deliberately excluded — SVG can embed JavaScript.
- Only the backend has bucket credentials; the frontend never talks to MinIO directly.
- `path-style-access: true` is required for MinIO; production S3 flips it off. Configured in `application.yml`.
- 10 MB cap enforced at Spring multipart layer — oversize requests 413 before reaching service code.
- Downloads authenticated via the existing JWT filter; `AttachmentService` enforces room membership / conversation participation.

## Risk

- **Testcontainers + MinIO** — exact API depends on which testcontainers version is pinned. Plan step will verify the MinIO image pulls in CI and fall back to an in-memory stub if it's flaky.
- **Storage credential config** — defaults (`minioadmin`/`minioadmin`) are fine for dev; real deployment must override via env vars. Clearly documented in the plan's deployment notes.
- **Hackathon deadline** — feature is ~4 h scoped (backend + frontend + tests); if time gets tight, the Playwright spec can be trimmed to a single room-upload scenario.

## Plan outline

1. V8 migration (two attachment tables) + MinIO service in compose + application.yml config keys
2. `StorageService` + `S3StorageService` + `StorageConfig` + `BucketInitializer` + round-trip test (testcontainers MinIO or in-memory stub)
3. Entities + repositories + `AttachmentSummary` record + DTO fields (both `ChatMessageDTO` and `DirectMessageDTO`) + service `toDto` population (no behaviour yet, all-null)
4. Room side: `AttachmentPolicy` + `MessageService` send-with-file + soft-delete S3 cleanup + multipart endpoint on `MessageController` + tests
5. DM side: same mirror
6. `AttachmentService` (lookup + auth) + `AttachmentController` + tests
7. Backend integration test (`AttachmentFlowIntegrationTest`)
8. Frontend types + `messageService.sendMessageWithAttachment` + DM mirror
9. Composer attach button + staged-file chip + wire into `ChatPage` and `DirectChatPage`
10. `MessageItem` image/download rendering + `DirectChatPage` adapter field
11. Playwright `attachments.spec.ts`
12. Roadmap update

## Verification checklist

- [ ] `docker compose up -d --build` brings up MinIO + backend cleanly; bucket exists on first boot
- [ ] `./gradlew test` green (including attachment integration test)
- [ ] `npm run build` clean, `npm test -- --run` green
- [ ] `npm run test:e2e` green (including `attachments.spec.ts`)
- [ ] Browser smoke: upload a PNG in a room, two tabs both see the image; upload a PDF in a DM, other side downloads; try >10 MB, see error toast
- [ ] `FEATURES_ROADMAP.md` reflects Feature #6 COMPLETE
