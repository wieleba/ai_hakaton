# Attachments (Feature #6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attach one file to a chat message (room or DM); images render inline, other allowed files render as download links. Storage is S3-compatible — MinIO locally, any S3 endpoint in prod — so every backend replica sees the same bytes.

**Architecture:** V8 migration adds `message_attachments` + `direct_message_attachments` tables with FK CASCADE. A `StorageService` abstraction with an `S3StorageService` implementation (AWS SDK v2) talks to MinIO. Messages with attachments use a new multipart REST POST on the existing message URLs; plain text keeps using WebSocket. Downloads go through a backend-proxied `GET /api/attachments/{id}/content` that authorizes room/conversation membership. The test profile swaps in an `InMemoryStorageService` so tests don't need MinIO.

**Tech Stack:** Spring Boot 3.5.12, Java 25, PostgreSQL 15, Flyway, AWS SDK v2 S3 client, MinIO; React 19, TypeScript, Vite, axios (multipart); Playwright.

**Spec:** `docs/superpowers/specs/2026-04-19-attachments-design.md`

---

## File Structure

### Backend (new)

```
backend/src/main/resources/db/migration/
  V8__message_attachments.sql

backend/src/main/java/com/hackathon/shared/storage/
  StorageService.java                 (interface)
  S3StorageService.java                (S3 SDK impl; default profile)
  InMemoryStorageService.java          (@Profile("test"))
  StorageProperties.java               (@ConfigurationProperties("storage.s3"))
  StorageConfig.java                   (@Configuration — builds S3Client bean + bucket bootstrap)

backend/src/main/java/com/hackathon/features/attachments/
  AttachmentPolicy.java                (MIME allow-list + size const + isImage)
  AttachmentController.java            (GET /api/attachments/{id}/content)
  AttachmentService.java               (lookup across both tables + auth helper)
  AttachmentLookupResult.java          (record: which table, message id, roomId or convId)

backend/src/main/java/com/hackathon/features/messages/
  MessageAttachment.java
  MessageAttachmentRepository.java

backend/src/main/java/com/hackathon/features/dms/
  DirectMessageAttachment.java
  DirectMessageAttachmentRepository.java

backend/src/test/java/com/hackathon/features/attachments/
  AttachmentPolicyTest.java
  AttachmentServiceTest.java
  AttachmentControllerTest.java

backend/src/test/java/com/hackathon/shared/storage/
  InMemoryStorageServiceTest.java

backend/src/test/java/com/hackathon/features/integration/
  AttachmentFlowIntegrationTest.java
```

### Backend (modified)

```
backend/build.gradle                                     (+software.amazon.awssdk:s3)
backend/src/main/resources/application.yml               (+storage config, +multipart limits)
backend/src/main/java/com/hackathon/shared/dto/
  AttachmentSummary.java                                 (new — record)
  ChatMessageDTO.java                                    (+attachment field)
  DirectMessageDTO.java                                  (+attachment field)
backend/src/main/java/com/hackathon/features/messages/
  MessageService.java                                    (+send-with-file + attachment lookup in toDto + soft-delete cleanup)
  MessageController.java                                 (+multipart @PostMapping overload)
backend/src/main/java/com/hackathon/features/dms/
  DirectMessageService.java                              (same)
  DirectMessageController.java                           (same)
docker-compose.yml                                       (+minio service, +minio_data volume, +MINIO_ENDPOINT env on backend)
```

### Frontend (new)

```
frontend/src/types/attachment.ts
frontend/src/components/ComposerAttachButton.tsx
frontend/src/components/AttachmentPreviewChip.tsx
frontend/src/components/AttachmentRenderer.tsx
frontend/e2e/attachments.spec.ts
```

### Frontend (modified)

```
frontend/src/types/room.ts                               (+attachment field on Message)
frontend/src/types/directMessage.ts                      (+attachment field on DirectMessage)
frontend/src/services/messageService.ts                  (+sendMessageWithAttachment)
frontend/src/services/directMessageService.ts            (+sendMessageWithAttachment)
frontend/src/components/MessageItem.tsx                  (renders AttachmentRenderer)
frontend/src/pages/ChatPage.tsx                          (stages file, picks REST vs WS send path)
frontend/src/pages/DirectChatPage.tsx                    (same + adapter carries attachment)
FEATURES_ROADMAP.md                                      (Feature #6 → COMPLETE)
```

---

## Implementation Tasks

### Task 1: V8 migration + MinIO compose + application.yml config

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__message_attachments.sql`
- Modify: `docker-compose.yml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/build.gradle`

- [ ] **Step 1: Create the migration**

`backend/src/main/resources/db/migration/V8__message_attachments.sql`:

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

- [ ] **Step 2: Add MinIO to `docker-compose.yml`**

Add the service below the `postgres` block and add the volume. Also add `STORAGE_S3_ENDPOINT` env var on the `backend` service.

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

In the existing `backend` service block, add to `environment:`:

```yaml
      STORAGE_S3_ENDPOINT: http://minio:9000
      STORAGE_S3_ACCESS_KEY: minioadmin
      STORAGE_S3_SECRET_KEY: minioadmin
      STORAGE_S3_BUCKET: chat-attachments
```

And add `minio_data:` under the top-level `volumes:` block (next to `postgres_data:`).

- [ ] **Step 3: Add storage + multipart config to `application.yml`**

Append to `backend/src/main/resources/application.yml`:

```yaml
storage:
  s3:
    endpoint: ${STORAGE_S3_ENDPOINT:http://localhost:9000}
    region: us-east-1
    access-key: ${STORAGE_S3_ACCESS_KEY:minioadmin}
    secret-key: ${STORAGE_S3_SECRET_KEY:minioadmin}
    bucket: ${STORAGE_S3_BUCKET:chat-attachments}
    path-style-access: true
```

Add to the existing `spring:` block (merge with current `spring.jpa`, `spring.flyway`, etc.):

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
```

- [ ] **Step 4: Add AWS SDK dep to `backend/build.gradle`**

Inside `dependencies { ... }`, after the JWT lines:

```gradle
    // Object storage (S3-compatible — MinIO in dev, any S3 endpoint in prod)
    implementation platform('software.amazon.awssdk:bom:2.28.17')
    implementation 'software.amazon.awssdk:s3'
```

- [ ] **Step 5: Verify compile**

```bash
cd /src/ai_hakaton/backend && ./gradlew compileJava compileTestJava
```

Expected: clean — no code refers to the S3 SDK yet; migration is SQL; docker-compose and application.yml changes don't affect the build. (Flyway will validate against the current schema on boot, but the Java schema assertions don't check the new tables because no entity references them yet.)

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/resources/db/migration/V8__message_attachments.sql \
        backend/build.gradle \
        backend/src/main/resources/application.yml \
        docker-compose.yml
git commit -m "chore(attachments): V8 schema + MinIO in compose + S3 SDK dep" -m "New message_attachments and direct_message_attachments tables with FK CASCADE. MinIO service in docker-compose for S3-compatible dev storage. AWS SDK v2 S3 client added. Spring multipart capped at 10MB per file." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `StorageService` abstraction + in-memory + S3 impl + bucket bootstrap

**Files:**
- Create: `backend/src/main/java/com/hackathon/shared/storage/StorageService.java`
- Create: `backend/src/main/java/com/hackathon/shared/storage/StorageProperties.java`
- Create: `backend/src/main/java/com/hackathon/shared/storage/StorageConfig.java`
- Create: `backend/src/main/java/com/hackathon/shared/storage/S3StorageService.java`
- Create: `backend/src/main/java/com/hackathon/shared/storage/InMemoryStorageService.java`
- Create: `backend/src/test/java/com/hackathon/shared/storage/InMemoryStorageServiceTest.java`

- [ ] **Step 1: `StorageService.java`**

```java
package com.hackathon.shared.storage;

import java.io.InputStream;

public interface StorageService {
  /** Stores bytes and returns a storage key (UUID-shaped). */
  String store(InputStream content, long size, String mimeType);

  /** Streams bytes for a stored object. Throws if key unknown. */
  InputStream load(String storageKey);

  /** Idempotent — deleting an unknown key is a no-op. */
  void delete(String storageKey);
}
```

- [ ] **Step 2: `StorageProperties.java`**

```java
package com.hackathon.shared.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage.s3")
public class StorageProperties {
  private String endpoint;
  private String region;
  private String accessKey;
  private String secretKey;
  private String bucket;
  private boolean pathStyleAccess = true;
}
```

- [ ] **Step 3: `StorageConfig.java`**

```java
package com.hackathon.shared.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  @Bean
  @Profile("!test")
  public S3Client s3Client(StorageProperties props) {
    return S3Client.builder()
        .endpointOverride(URI.create(props.getEndpoint()))
        .region(Region.of(props.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(props.isPathStyleAccess()).build())
        .build();
  }
}
```

- [ ] **Step 4: `S3StorageService.java`**

```java
package com.hackathon.shared.storage;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {
  private final S3Client s3;
  private final StorageProperties props;

  @PostConstruct
  void ensureBucket() {
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(props.getBucket()).build());
      log.info("Storage bucket {} exists", props.getBucket());
    } catch (NoSuchBucketException e) {
      log.info("Creating storage bucket {}", props.getBucket());
      s3.createBucket(CreateBucketRequest.builder().bucket(props.getBucket()).build());
    } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
      if (e.statusCode() == 404) {
        log.info("Creating storage bucket {}", props.getBucket());
        s3.createBucket(CreateBucketRequest.builder().bucket(props.getBucket()).build());
      } else {
        throw e;
      }
    }
  }

  @Override
  public String store(InputStream content, long size, String mimeType) {
    String key = UUID.randomUUID().toString();
    s3.putObject(
        PutObjectRequest.builder()
            .bucket(props.getBucket())
            .key(key)
            .contentType(mimeType)
            .contentLength(size)
            .build(),
        RequestBody.fromInputStream(content, size));
    return key;
  }

  @Override
  public InputStream load(String storageKey) {
    ResponseInputStream<GetObjectResponse> resp =
        s3.getObject(GetObjectRequest.builder().bucket(props.getBucket()).key(storageKey).build());
    return resp;
  }

  @Override
  public void delete(String storageKey) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(props.getBucket()).key(storageKey).build());
  }
}
```

- [ ] **Step 5: `InMemoryStorageService.java` (used by tests)**

```java
package com.hackathon.shared.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class InMemoryStorageService implements StorageService {
  private final ConcurrentMap<String, byte[]> objects = new ConcurrentHashMap<>();

  @Override
  public String store(InputStream content, long size, String mimeType) {
    try {
      byte[] bytes = content.readAllBytes();
      String key = UUID.randomUUID().toString();
      objects.put(key, bytes);
      return key;
    } catch (IOException e) {
      throw new RuntimeException("store failed", e);
    }
  }

  @Override
  public InputStream load(String storageKey) {
    byte[] bytes = objects.get(storageKey);
    if (bytes == null) {
      throw new IllegalArgumentException("Unknown storage key: " + storageKey);
    }
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public void delete(String storageKey) {
    objects.remove(storageKey);
  }
}
```

- [ ] **Step 6: `InMemoryStorageServiceTest.java`**

```java
package com.hackathon.shared.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InMemoryStorageServiceTest {
  @Autowired StorageService storage;

  @Test
  void storeThenLoad_returnsSameBytes() throws Exception {
    byte[] input = "hello world".getBytes();
    String key = storage.store(new ByteArrayInputStream(input), input.length, "text/plain");
    assertNotNull(key);
    byte[] out = storage.load(key).readAllBytes();
    assertArrayEquals(input, out);
  }

  @Test
  void deleteUnknownKey_isNoOp() {
    assertDoesNotThrow(() -> storage.delete("not-a-real-key"));
  }

  @Test
  void loadAfterDelete_throws() {
    byte[] input = "gone soon".getBytes();
    String key = storage.store(new ByteArrayInputStream(input), input.length, "text/plain");
    storage.delete(key);
    assertThrows(IllegalArgumentException.class, () -> storage.load(key));
  }
}
```

- [ ] **Step 7: Run the test**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'InMemoryStorageServiceTest'
```

Expected: 3 pass.

- [ ] **Step 8: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/shared/storage/ \
        backend/src/test/java/com/hackathon/shared/storage/
git commit -m "feat(storage): StorageService abstraction + S3 + in-memory impls + bootstrap" -m "StorageService.store/load/delete; S3StorageService for all non-test profiles (creates bucket on boot if missing); InMemoryStorageService for @Profile(test). 3 unit tests cover round-trip + idempotent delete." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Entities + repositories + DTO `attachment` field + AttachmentSummary (no behaviour yet)

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/messages/MessageAttachment.java`
- Create: `backend/src/main/java/com/hackathon/features/messages/MessageAttachmentRepository.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectMessageAttachment.java`
- Create: `backend/src/main/java/com/hackathon/features/dms/DirectMessageAttachmentRepository.java`
- Create: `backend/src/main/java/com/hackathon/shared/dto/AttachmentSummary.java`
- Modify: `backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java`
- Modify: `backend/src/main/java/com/hackathon/shared/dto/DirectMessageDTO.java`

All five new files follow the pattern already used by `MessageReaction` / `DirectMessageReaction`.

- [ ] **Step 1: `MessageAttachment.java`**

```java
package com.hackathon.features.messages;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "message_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAttachment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(nullable = false, length = 255)
  private String filename;

  @Column(name = "mime_type", nullable = false, length = 128)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "storage_key", nullable = false, length = 255)
  private String storageKey;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
```

- [ ] **Step 2: `MessageAttachmentRepository.java`**

```java
package com.hackathon.features.messages;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
  Optional<MessageAttachment> findByMessageId(UUID messageId);
}
```

- [ ] **Step 3: `DirectMessageAttachment.java`**

```java
package com.hackathon.features.dms;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "direct_message_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageAttachment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "direct_message_id", nullable = false)
  private UUID directMessageId;

  @Column(nullable = false, length = 255)
  private String filename;

  @Column(name = "mime_type", nullable = false, length = 128)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "storage_key", nullable = false, length = 255)
  private String storageKey;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
```

- [ ] **Step 4: `DirectMessageAttachmentRepository.java`**

```java
package com.hackathon.features.dms;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageAttachmentRepository
    extends JpaRepository<DirectMessageAttachment, UUID> {
  Optional<DirectMessageAttachment> findByDirectMessageId(UUID directMessageId);
}
```

- [ ] **Step 5: `AttachmentSummary.java`**

```java
package com.hackathon.shared.dto;

import java.util.UUID;

public record AttachmentSummary(UUID id, String filename, String mimeType, long sizeBytes) {}
```

- [ ] **Step 6: Extend `ChatMessageDTO.java`**

Add an `attachment` field (preserve all existing fields):

```java
  private AttachmentSummary attachment;
```

- [ ] **Step 7: Extend `DirectMessageDTO.java`** — same:

```java
  private AttachmentSummary attachment;
```

- [ ] **Step 8: Verify compile**

```bash
cd /src/ai_hakaton/backend && ./gradlew compileJava compileTestJava
```

Expected: clean. No service changes yet; `attachment` stays null in all existing callers.

- [ ] **Step 9: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/messages/MessageAttachment.java \
        backend/src/main/java/com/hackathon/features/messages/MessageAttachmentRepository.java \
        backend/src/main/java/com/hackathon/features/dms/DirectMessageAttachment.java \
        backend/src/main/java/com/hackathon/features/dms/DirectMessageAttachmentRepository.java \
        backend/src/main/java/com/hackathon/shared/dto/AttachmentSummary.java \
        backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java \
        backend/src/main/java/com/hackathon/shared/dto/DirectMessageDTO.java
git commit -m "feat(attachments): entities + repositories + AttachmentSummary DTO field" -m "MessageAttachment + DirectMessageAttachment JPA entities matching the V8 schema. Derived-query repositories with findByMessageId / findByDirectMessageId. ChatMessageDTO + DirectMessageDTO gain an 'attachment' AttachmentSummary field (null until services populate it in subsequent tasks)." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Room side — `AttachmentPolicy` + `MessageService` send-with-file + soft-delete cleanup + multipart endpoint + tests

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/attachments/AttachmentPolicy.java`
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageService.java`
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageController.java`
- Modify: `backend/src/test/java/com/hackathon/features/messages/MessageServiceTest.java`
- Modify: `backend/src/test/java/com/hackathon/features/messages/MessageControllerTest.java`

- [ ] **Step 1: `AttachmentPolicy.java`**

```java
package com.hackathon.features.attachments;

import java.util.Set;

public final class AttachmentPolicy {
  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

  public static final Set<String> IMAGE_MIMES =
      Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

  public static final Set<String> DOCUMENT_MIMES =
      Set.of("application/pdf", "text/plain", "application/zip");

  public static boolean isImage(String mime) {
    return mime != null && IMAGE_MIMES.contains(mime);
  }

  public static boolean isAllowed(String mime) {
    return mime != null && (IMAGE_MIMES.contains(mime) || DOCUMENT_MIMES.contains(mime));
  }

  private AttachmentPolicy() {}
}
```

- [ ] **Step 2: Extend `MessageService.java`**

Add imports:

```java
import com.hackathon.features.attachments.AttachmentPolicy;
import com.hackathon.features.messages.MessageAttachment;
import com.hackathon.features.messages.MessageAttachmentRepository;
import com.hackathon.shared.dto.AttachmentSummary;
import com.hackathon.shared.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
```

Inject new deps (add to the `@RequiredArgsConstructor`-managed fields — order matches existing style):

```java
  private final MessageAttachmentRepository attachmentRepository;
  private final StorageService storageService;
```

Add the new `sendMessage` overload:

```java
  @Transactional
  public Message sendMessage(
      UUID roomId,
      UUID userId,
      String text,
      UUID replyToId,
      String filename,
      String mimeType,
      long size,
      InputStream content) {
    if (!roomMemberService.isMember(roomId, userId)) {
      throw new IllegalArgumentException("User is not a member of this room");
    }
    // Text is optional when a file is attached; otherwise validate as before.
    boolean hasText = text != null && !text.trim().isEmpty();
    boolean hasFile = content != null;
    if (!hasText && !hasFile) {
      throw new IllegalArgumentException("Message must contain text or a file");
    }
    if (hasText && text.length() > MAX_MESSAGE_SIZE) {
      throw new IllegalArgumentException(
          "Message exceeds maximum size of " + MAX_MESSAGE_SIZE + " bytes");
    }
    if (hasFile) {
      if (!AttachmentPolicy.isAllowed(mimeType)) {
        throw new IllegalArgumentException("File type not allowed: " + mimeType);
      }
      if (size <= 0 || size > AttachmentPolicy.MAX_SIZE_BYTES) {
        throw new IllegalArgumentException("File size outside allowed range");
      }
    }
    if (replyToId != null) {
      Message parent = messageRepository
          .findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getRoomId().equals(roomId)) {
        throw new IllegalArgumentException("Reply target is in a different room");
      }
    }
    Message saved = messageRepository.save(
        Message.builder()
            .roomId(roomId)
            .userId(userId)
            .text(hasText ? text : "")
            .replyToId(replyToId)
            .build());

    if (hasFile) {
      String storageKey = storageService.store(content, size, mimeType);
      MessageAttachment att = MessageAttachment.builder()
          .messageId(saved.getId())
          .filename(filename)
          .mimeType(mimeType)
          .sizeBytes(size)
          .storageKey(storageKey)
          .build();
      attachmentRepository.save(att);
    }

    publish(MessageEventEnvelope.created(toDto(saved)));
    return saved;
  }
```

Update `deleteMessage` to clean up the attachment + S3 object on soft-delete. Inside the existing method, after the `!m.getUserId().equals(callerId)` check and before setting `deletedAt`, add:

```java
    Optional<MessageAttachment> att = attachmentRepository.findByMessageId(messageId);
    if (att.isPresent()) {
      storageService.delete(att.get().getStorageKey());
      attachmentRepository.delete(att.get());
    }
```

Update `toDto(Message m, UUID callerId)` to populate `attachment`. Before the final `.build()` call, add:

```java
    AttachmentSummary attachmentSummary = null;
    if (m.getDeletedAt() == null) {
      attachmentSummary = attachmentRepository.findByMessageId(m.getId())
          .map(a -> new AttachmentSummary(a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes()))
          .orElse(null);
    }
```

And add `.attachment(attachmentSummary)` in the builder chain before `.build()`.

- [ ] **Step 3: Extend `MessageController.java`**

Add imports:

```java
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
```

Add the multipart variant (a new method on the existing controller — Spring dispatches by `Content-Type`):

```java
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ChatMessageDTO> sendMessageMultipart(
      @PathVariable UUID roomId,
      @RequestParam(value = "text", required = false) String text,
      @RequestParam(value = "replyToId", required = false) UUID replyToId,
      @RequestParam(value = "file", required = false) MultipartFile file,
      Authentication authentication) throws IOException {
    UUID userId = currentUserId(authentication);
    String filename = file != null ? file.getOriginalFilename() : null;
    String mimeType = file != null ? file.getContentType() : null;
    long size = file != null ? file.getSize() : 0L;
    java.io.InputStream content = file != null ? file.getInputStream() : null;
    try {
      Message message =
          messageService.sendMessage(roomId, userId, text, replyToId, filename, mimeType, size, content);
      return ResponseEntity.ok(messageService.toDto(message));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }
```

Leave the existing JSON `@PostMapping` untouched.

- [ ] **Step 4: Extend `MessageServiceTest.java`**

Append tests (match the SpringBootTest style already there). If there's no `storageService` autowired, the test profile's `InMemoryStorageService` bean is picked up automatically via `@ActiveProfiles("test")`.

```java
  @Test
  void sendMessage_withAttachment_storesFile_andPopulatesDto() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    byte[] bytes = new byte[]{1, 2, 3, 4};
    Message m = messageService.sendMessage(
        room.getId(), author.getId(), "with file",
        null, "hello.png", "image/png", bytes.length,
        new java.io.ByteArrayInputStream(bytes));
    ChatMessageDTO dto = messageService.toDto(m);
    assertNotNull(dto.getAttachment());
    assertEquals("hello.png", dto.getAttachment().filename());
    assertEquals("image/png", dto.getAttachment().mimeType());
    assertEquals(4L, dto.getAttachment().sizeBytes());
  }

  @Test
  void sendMessage_withBadMime_rejected() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    byte[] bytes = "<svg/>".getBytes();
    assertThrows(IllegalArgumentException.class,
        () -> messageService.sendMessage(
            room.getId(), author.getId(), null,
            null, "evil.svg", "image/svg+xml", bytes.length,
            new java.io.ByteArrayInputStream(bytes)));
  }

  @Test
  void sendMessage_noTextNoFile_rejected() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    assertThrows(IllegalArgumentException.class,
        () -> messageService.sendMessage(
            room.getId(), author.getId(), null, null, null, null, 0, null));
  }

  @Test
  void deleteMessage_withAttachment_removesAttachmentRow() {
    User author = registerUser("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");
    byte[] bytes = "hi".getBytes();
    Message m = messageService.sendMessage(
        room.getId(), author.getId(), null, null,
        "file.txt", "text/plain", bytes.length,
        new java.io.ByteArrayInputStream(bytes));
    assertTrue(attachmentRepository.findByMessageId(m.getId()).isPresent());
    messageService.deleteMessage(m.getId(), author.getId());
    assertTrue(attachmentRepository.findByMessageId(m.getId()).isEmpty());
  }
```

Add `@Autowired MessageAttachmentRepository attachmentRepository;` at the top of the class if not already there.

- [ ] **Step 5: Extend `MessageControllerTest.java`** — multipart happy + rejection

Append:

```java
  @Test
  void sendMessage_multipart_withImage_returnsDtoWithAttachment() throws Exception {
    User author = registerUser("a");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");

    var filePart = new org.springframework.mock.web.MockMultipartFile(
        "file", "pic.png", "image/png", new byte[]{1, 2, 3});

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/rooms/{roomId}/messages", room.getId())
                .file(filePart)
                .param("text", "hello")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachment.filename").value("pic.png"))
        .andExpect(jsonPath("$.attachment.mimeType").value("image/png"));
  }

  @Test
  void sendMessage_multipart_withBadMime_returns400() throws Exception {
    User author = registerUser("a");
    String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, author.getId(), "public");

    var filePart = new org.springframework.mock.web.MockMultipartFile(
        "file", "evil.svg", "image/svg+xml", "<svg/>".getBytes());

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/rooms/{roomId}/messages", room.getId())
                .file(filePart)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
  }
```

- [ ] **Step 6: Run targeted tests**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'MessageServiceTest' --tests 'MessageControllerTest'
```

Expected: all pass (existing + new).

- [ ] **Step 7: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/attachments/AttachmentPolicy.java \
        backend/src/main/java/com/hackathon/features/messages/MessageService.java \
        backend/src/main/java/com/hackathon/features/messages/MessageController.java \
        backend/src/test/java/com/hackathon/features/messages/MessageServiceTest.java \
        backend/src/test/java/com/hackathon/features/messages/MessageControllerTest.java
git commit -m "feat(attachments): room message send-with-file + soft-delete cleanup" -m "AttachmentPolicy (10MB cap + image/pdf/txt/zip allow-list). MessageService.sendMessage(..., filename, mime, size, content) validates + stores via StorageService and persists the attachment row in one transaction; toDto populates the attachment field; deleteMessage unlinks the object + deletes the row. MessageController gains a multipart @PostMapping variant alongside the existing JSON handler; Spring dispatches by Content-Type." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: DM side — mirror of Task 4

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java`
- Modify: `backend/src/main/java/com/hackathon/features/dms/DirectMessageController.java`
- Modify: `backend/src/test/java/com/hackathon/features/dms/DirectMessageServiceTest.java`
- Modify: `backend/src/test/java/com/hackathon/features/dms/DirectMessageControllerTest.java`

- [ ] **Step 1: Extend `DirectMessageService.java`**

Inject deps at the top of the class:

```java
  private final DirectMessageAttachmentRepository attachmentRepository;
  private final StorageService storageService;
```

(Also add imports: `AttachmentPolicy`, `AttachmentSummary`, `StorageService`, `java.io.InputStream`.)

Add the new overload (mirrors room):

```java
  @Transactional
  public DirectMessage send(
      UUID senderId,
      UUID conversationId,
      String text,
      UUID replyToId,
      String filename,
      String mimeType,
      long size,
      InputStream content) {
    DirectConversation conv = loadConversation(conversationId);
    if (!conv.getUser1Id().equals(senderId) && !conv.getUser2Id().equals(senderId)) {
      throw new IllegalArgumentException("Not a participant of this conversation");
    }
    UUID other = conversationService.otherParticipant(conv, senderId);
    ensureFriendsAndNotBanned(senderId, other);

    boolean hasText = text != null && !text.trim().isEmpty();
    boolean hasFile = content != null;
    if (!hasText && !hasFile) {
      throw new IllegalArgumentException("Message must contain text or a file");
    }
    if (hasText && text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("Message exceeds 3072 characters");
    }
    if (hasFile) {
      if (!AttachmentPolicy.isAllowed(mimeType)) {
        throw new IllegalArgumentException("File type not allowed: " + mimeType);
      }
      if (size <= 0 || size > AttachmentPolicy.MAX_SIZE_BYTES) {
        throw new IllegalArgumentException("File size outside allowed range");
      }
    }
    if (replyToId != null) {
      DirectMessage parent = directMessageRepository
          .findById(replyToId)
          .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
      if (!parent.getConversationId().equals(conversationId)) {
        throw new IllegalArgumentException("Reply target is in a different conversation");
      }
    }
    DirectMessage saved = directMessageRepository.save(
        DirectMessage.builder()
            .conversationId(conversationId)
            .senderId(senderId)
            .text(hasText ? text : "")
            .replyToId(replyToId)
            .build());

    if (hasFile) {
      String storageKey = storageService.store(content, size, mimeType);
      attachmentRepository.save(
          DirectMessageAttachment.builder()
              .directMessageId(saved.getId())
              .filename(filename)
              .mimeType(mimeType)
              .sizeBytes(size)
              .storageKey(storageKey)
              .build());
    }
    publishToBoth(senderId, other, DirectMessageEventEnvelope.created(toDto(saved)));
    return saved;
  }
```

Update `deleteMessage` to clean the attachment (same pattern as room):

```java
    Optional<DirectMessageAttachment> att = attachmentRepository.findByDirectMessageId(messageId);
    if (att.isPresent()) {
      storageService.delete(att.get().getStorageKey());
      attachmentRepository.delete(att.get());
    }
```

Inject inside `deleteMessage` before the `deletedAt` assignment.

Update `toDto(DirectMessage m, UUID callerId)`:

```java
    AttachmentSummary attachmentSummary = null;
    if (m.getDeletedAt() == null) {
      attachmentSummary = attachmentRepository.findByDirectMessageId(m.getId())
          .map(a -> new AttachmentSummary(a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes()))
          .orElse(null);
    }
```

And include `.attachment(attachmentSummary)` in the builder chain.

- [ ] **Step 2: Extend `DirectMessageController.java`**

Add imports: `MediaType`, `MultipartFile`, `IOException`.

Add the multipart handler (mirror of room's):

```java
  @PostMapping(path = "/{conversationId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DirectMessageDTO> sendMessageMultipart(
      @PathVariable UUID conversationId,
      @RequestParam(value = "text", required = false) String text,
      @RequestParam(value = "replyToId", required = false) UUID replyToId,
      @RequestParam(value = "file", required = false) MultipartFile file,
      Authentication authentication) throws java.io.IOException {
    String filename = file != null ? file.getOriginalFilename() : null;
    String mimeType = file != null ? file.getContentType() : null;
    long size = file != null ? file.getSize() : 0L;
    java.io.InputStream content = file != null ? file.getInputStream() : null;
    try {
      DirectMessage sent = directMessageService.send(
          currentUserId(authentication), conversationId,
          text, replyToId, filename, mimeType, size, content);
      return ResponseEntity.ok(directMessageService.toDto(sent));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }
```

Leave the existing JSON `@PostMapping` untouched.

- [ ] **Step 3: Extend `DirectMessageServiceTest.java`**

Append (mirrors Task 4 Step 4 for DMs — `makeFriends` helper already present in that file):

```java
  @Test
  void send_withAttachment_storesFile_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = new byte[]{1, 2, 3};
    DirectMessage m = directMessageService.send(
        a.getId(), conv.getId(), "hi",
        null, "pic.png", "image/png", bytes.length,
        new java.io.ByteArrayInputStream(bytes));
    DirectMessageDTO dto = directMessageService.toDto(m);
    assertNotNull(dto.getAttachment());
    assertEquals("pic.png", dto.getAttachment().filename());
  }

  @Test
  void send_withBadMime_rejected_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = "<svg/>".getBytes();
    assertThrows(IllegalArgumentException.class,
        () -> directMessageService.send(
            a.getId(), conv.getId(), null, null,
            "x.svg", "image/svg+xml", bytes.length,
            new java.io.ByteArrayInputStream(bytes)));
  }

  @Test
  void delete_withAttachment_removesRow_dm() {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = "hi".getBytes();
    DirectMessage m = directMessageService.send(
        a.getId(), conv.getId(), null, null,
        "hi.txt", "text/plain", bytes.length,
        new java.io.ByteArrayInputStream(bytes));
    assertTrue(attachmentRepository.findByDirectMessageId(m.getId()).isPresent());
    directMessageService.deleteMessage(m.getId(), a.getId());
    assertTrue(attachmentRepository.findByDirectMessageId(m.getId()).isEmpty());
  }
```

Autowire `DirectMessageAttachmentRepository attachmentRepository;` if missing.

- [ ] **Step 4: Extend `DirectMessageControllerTest.java`** — one multipart happy + one rejection (same shape as Task 4 Step 5 but for DMs + `makeFriends`).

Append:

```java
  @Test
  void sendMessage_multipart_withImage_returnsDtoWithAttachment_dm() throws Exception {
    User a = registerUser("a");
    User b = registerUser("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    String token = jwtTokenProvider.generateToken(a.getId(), a.getUsername());

    var filePart = new org.springframework.mock.web.MockMultipartFile(
        "file", "pic.png", "image/png", new byte[]{1, 2, 3});

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/dms/{cid}/messages", conv.getId())
                .file(filePart)
                .param("text", "hi")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachment.filename").value("pic.png"));
  }
```

- [ ] **Step 5: Run targeted tests**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'DirectMessageServiceTest' --tests 'DirectMessageControllerTest'
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java \
        backend/src/main/java/com/hackathon/features/dms/DirectMessageController.java \
        backend/src/test/java/com/hackathon/features/dms/DirectMessageServiceTest.java \
        backend/src/test/java/com/hackathon/features/dms/DirectMessageControllerTest.java
git commit -m "feat(attachments): DM message send-with-file + soft-delete cleanup" -m "Mirror of the room implementation: DirectMessageService.send overload validates MIME + size + persists attachment; toDto populates the summary; deleteMessage unlinks the object and row. DirectMessageController gains a multipart POST on /api/dms/{cid}/messages; JSON handler unchanged." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: `AttachmentService` + `AttachmentController` (download endpoint) + tests

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/attachments/AttachmentLookupResult.java`
- Create: `backend/src/main/java/com/hackathon/features/attachments/AttachmentService.java`
- Create: `backend/src/main/java/com/hackathon/features/attachments/AttachmentController.java`
- Create: `backend/src/test/java/com/hackathon/features/attachments/AttachmentControllerTest.java`

- [ ] **Step 1: `AttachmentLookupResult.java`**

```java
package com.hackathon.features.attachments;

import java.util.UUID;

public record AttachmentLookupResult(
    UUID attachmentId,
    String filename,
    String mimeType,
    long sizeBytes,
    String storageKey,
    Scope scope,
    UUID scopeId /* roomId or conversationId */) {

  public enum Scope {
    ROOM,
    DIRECT
  }
}
```

- [ ] **Step 2: `AttachmentService.java`**

```java
package com.hackathon.features.attachments;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversationRepository;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageAttachment;
import com.hackathon.features.dms.DirectMessageAttachmentRepository;
import com.hackathon.features.dms.DirectMessageRepository;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageAttachment;
import com.hackathon.features.messages.MessageAttachmentRepository;
import com.hackathon.features.messages.MessageRepository;
import com.hackathon.features.rooms.RoomMemberService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttachmentService {
  private final MessageAttachmentRepository messageAttachmentRepository;
  private final DirectMessageAttachmentRepository directMessageAttachmentRepository;
  private final MessageRepository messageRepository;
  private final DirectMessageRepository directMessageRepository;
  private final DirectConversationRepository directConversationRepository;
  private final RoomMemberService roomMemberService;

  public Optional<AttachmentLookupResult> lookup(UUID attachmentId) {
    Optional<MessageAttachment> room = messageAttachmentRepository.findById(attachmentId);
    if (room.isPresent()) {
      MessageAttachment a = room.get();
      Message parent = messageRepository.findById(a.getMessageId()).orElse(null);
      if (parent == null) return Optional.empty();
      return Optional.of(new AttachmentLookupResult(
          a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes(), a.getStorageKey(),
          AttachmentLookupResult.Scope.ROOM, parent.getRoomId()));
    }
    Optional<DirectMessageAttachment> dm = directMessageAttachmentRepository.findById(attachmentId);
    if (dm.isPresent()) {
      DirectMessageAttachment a = dm.get();
      DirectMessage parent = directMessageRepository.findById(a.getDirectMessageId()).orElse(null);
      if (parent == null) return Optional.empty();
      return Optional.of(new AttachmentLookupResult(
          a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes(), a.getStorageKey(),
          AttachmentLookupResult.Scope.DIRECT, parent.getConversationId()));
    }
    return Optional.empty();
  }

  public boolean isAuthorized(AttachmentLookupResult hit, UUID callerId) {
    if (hit.scope() == AttachmentLookupResult.Scope.ROOM) {
      return roomMemberService.isMember(hit.scopeId(), callerId);
    }
    return directConversationRepository
        .findById(hit.scopeId())
        .map(c -> c.getUser1Id().equals(callerId) || c.getUser2Id().equals(callerId))
        .orElse(false);
  }
}
```

- [ ] **Step 3: `AttachmentController.java`**

```java
package com.hackathon.features.attachments;

import com.hackathon.features.users.UserService;
import com.hackathon.shared.storage.StorageService;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {
  private final AttachmentService attachmentService;
  private final StorageService storageService;
  private final UserService userService;

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping("/{id}/content")
  public ResponseEntity<InputStreamResource> getContent(
      @PathVariable UUID id, Authentication authentication) {
    Optional<AttachmentLookupResult> hit = attachmentService.lookup(id);
    if (hit.isEmpty()) return ResponseEntity.notFound().build();
    if (!attachmentService.isAuthorized(hit.get(), currentUserId(authentication))) {
      return ResponseEntity.status(403).build();
    }
    AttachmentLookupResult a = hit.get();
    InputStream content = storageService.load(a.storageKey());

    String disposition = AttachmentPolicy.isImage(a.mimeType()) ? "inline" : "attachment";
    String encoded = URLEncoder.encode(a.filename(), StandardCharsets.UTF_8).replace("+", "%20");
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_DISPOSITION,
        disposition + "; filename*=UTF-8''" + encoded);
    headers.setContentLength(a.sizeBytes());

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType(a.mimeType()))
        .body(new InputStreamResource(content));
  }
}
```

- [ ] **Step 4: `AttachmentControllerTest.java`**

```java
package com.hackathon.features.attachments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.security.JwtTokenProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired MessageService messageService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void getContent_memberGetsInlineImage() throws Exception {
    User alice = register("alice");
    String token = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] png = new byte[]{1, 2, 3, 4};
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "pic.png", "image/png", png.length, new java.io.ByteArrayInputStream(png));
    ChatMessageDTO dto = messageService.toDto(m);

    mvc.perform(get("/api/attachments/{id}/content", dto.getAttachment().id())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("inline")));
  }

  @Test
  void getContent_nonMember_403() throws Exception {
    User alice = register("alice");
    User intruder = register("intruder");
    String token = jwtTokenProvider.generateToken(intruder.getId(), intruder.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "private");
    byte[] png = new byte[]{1, 2, 3, 4};
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "pic.png", "image/png", png.length, new java.io.ByteArrayInputStream(png));
    ChatMessageDTO dto = messageService.toDto(m);

    mvc.perform(get("/api/attachments/{id}/content", dto.getAttachment().id())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void getContent_unknownId_404() throws Exception {
    User alice = register("alice");
    String token = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());
    mvc.perform(get("/api/attachments/{id}/content", java.util.UUID.randomUUID())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void getContent_noAuth_401() throws Exception {
    mvc.perform(get("/api/attachments/{id}/content", java.util.UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getContent_document_hasAttachmentDisposition() throws Exception {
    User alice = register("alice");
    String token = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] pdf = "%PDF-1.4 bytes".getBytes();
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "doc.pdf", "application/pdf", pdf.length, new java.io.ByteArrayInputStream(pdf));
    ChatMessageDTO dto = messageService.toDto(m);

    mvc.perform(get("/api/attachments/{id}/content", dto.getAttachment().id())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")));
  }
}
```

- [ ] **Step 5: Run targeted tests**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'AttachmentControllerTest'
```

Expected: 5 pass.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/attachments/AttachmentLookupResult.java \
        backend/src/main/java/com/hackathon/features/attachments/AttachmentService.java \
        backend/src/main/java/com/hackathon/features/attachments/AttachmentController.java \
        backend/src/test/java/com/hackathon/features/attachments/AttachmentControllerTest.java
git commit -m "feat(attachments): GET /api/attachments/{id}/content with auth + inline/attachment" -m "AttachmentService.lookup resolves an id across both message_attachments and direct_message_attachments; isAuthorized gates on room membership or conversation participation. AttachmentController streams bytes via the StorageService; Content-Disposition is inline for the image allow-list and attachment for everything else; filename is RFC-5987 encoded. 401/403/404/200 all covered by tests." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Backend integration test — `AttachmentFlowIntegrationTest`

**Files:**
- Create: `backend/src/test/java/com/hackathon/features/integration/AttachmentFlowIntegrationTest.java`

- [ ] **Step 1: Create the test**

```java
package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageAttachmentRepository;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageAttachmentRepository;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomRepository;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.dto.DirectMessageDTO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AttachmentFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired ChatRoomRepository chatRoomRepository;
  @Autowired MessageService messageService;
  @Autowired MessageAttachmentRepository messageAttachmentRepository;
  @Autowired DirectMessageService directMessageService;
  @Autowired DirectMessageAttachmentRepository directMessageAttachmentRepository;
  @Autowired ConversationService conversationService;
  @Autowired FriendshipService friendshipService;

  private User register(String s) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + s + "@example.com",
        "user" + t + s,
        "password12345");
  }

  private void makeFriends(User a, User b) {
    Friendship req = friendshipService.sendRequest(a.getId(), b.getUsername());
    friendshipService.accept(b.getId(), req.getId());
  }

  @Test
  void roomAttachment_softDelete_removesRowAndObject() {
    User alice = register("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] bytes = "image-bytes".getBytes();
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "pic.png", "image/png", bytes.length, new ByteArrayInputStream(bytes));
    ChatMessageDTO dto = messageService.toDto(m);
    assertNotNull(dto.getAttachment());
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isPresent());

    messageService.deleteMessage(m.getId(), alice.getId());
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isEmpty());
  }

  @Test
  void roomAttachment_hardCascadeOnRoomDelete_removesAttachmentRow() {
    User alice = register("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] bytes = "bytes".getBytes();
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "x.txt", "text/plain", bytes.length, new ByteArrayInputStream(bytes));
    ChatMessageDTO dto = messageService.toDto(m);
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isPresent());

    chatRoomService.deleteRoom(room.getId(), alice.getId());
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isEmpty());
  }

  @Test
  void dmAttachment_softDelete_removesRow() {
    User a = register("a");
    User b = register("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = "data".getBytes();
    DirectMessage m = directMessageService.send(a.getId(), conv.getId(), null, null,
        "doc.pdf", "application/pdf", bytes.length, new ByteArrayInputStream(bytes));
    DirectMessageDTO dto = directMessageService.toDto(m);
    assertNotNull(dto.getAttachment());

    directMessageService.deleteMessage(m.getId(), a.getId());
    assertTrue(directMessageAttachmentRepository.findById(dto.getAttachment().id()).isEmpty());
  }
}
```

- [ ] **Step 2: Run the integration test and the full backend suite**

```bash
cd /src/ai_hakaton/backend && ./gradlew test --tests 'AttachmentFlowIntegrationTest'
./gradlew test
```

Expected: both green.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/test/java/com/hackathon/features/integration/AttachmentFlowIntegrationTest.java
git commit -m "test: attachment lifecycle — soft-delete + hard cascade (rooms + DMs)" -m "Three scenarios: room soft-delete removes attachment row; room hard-delete cascades it via FK; DM soft-delete removes attachment row." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Frontend types + services (multipart send)

**Files:**
- Create: `frontend/src/types/attachment.ts`
- Modify: `frontend/src/types/room.ts`
- Modify: `frontend/src/types/directMessage.ts`
- Modify: `frontend/src/services/messageService.ts`
- Modify: `frontend/src/services/directMessageService.ts`

- [ ] **Step 1: `frontend/src/types/attachment.ts`**

```typescript
export interface AttachmentSummary {
  id: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
}

export const isImageMime = (m?: string | null): boolean =>
  !!m && /^image\/(png|jpeg|gif|webp)$/.test(m);

export const prettySize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};
```

- [ ] **Step 2: Extend `frontend/src/types/room.ts`**

At the top of the existing file, add the import:

```typescript
import type { AttachmentSummary } from './attachment';
```

In the existing `Message` interface, add the optional field (preserve all other fields):

```typescript
  attachment?: AttachmentSummary | null;
```

- [ ] **Step 3: Extend `frontend/src/types/directMessage.ts`**

Add the import + field the same way:

```typescript
import type { AttachmentSummary } from './attachment';
```

```typescript
  attachment?: AttachmentSummary | null;
```

- [ ] **Step 4: Extend `frontend/src/services/messageService.ts`**

Add a new method:

```typescript
  async sendMessageWithAttachment(
    roomId: string,
    text: string,
    file: File,
    replyToId?: string,
  ): Promise<Message> {
    const form = new FormData();
    if (text.trim().length > 0) form.append('text', text);
    if (replyToId) form.append('replyToId', replyToId);
    form.append('file', file);
    const response = await axios.post(`/api/rooms/${roomId}/messages`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
```

- [ ] **Step 5: Extend `frontend/src/services/directMessageService.ts`**

Same method for DMs:

```typescript
  async sendMessageWithAttachment(
    conversationId: string,
    text: string,
    file: File,
    replyToId?: string,
  ): Promise<DirectMessage> {
    const form = new FormData();
    if (text.trim().length > 0) form.append('text', text);
    if (replyToId) form.append('replyToId', replyToId);
    form.append('file', file);
    const response = await axios.post(`/api/dms/${conversationId}/messages`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
```

- [ ] **Step 6: Build + vitest**

```bash
cd /src/ai_hakaton/frontend && npm run build && npm test -- --run
```

Expected: clean + green.

- [ ] **Step 7: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/types/attachment.ts \
        frontend/src/types/room.ts \
        frontend/src/types/directMessage.ts \
        frontend/src/services/messageService.ts \
        frontend/src/services/directMessageService.ts
git commit -m "feat(frontend): attachment types + multipart send helpers" -m "New AttachmentSummary interface + isImageMime/prettySize helpers. Message + DirectMessage gain an optional attachment field. messageService + directMessageService gain sendMessageWithAttachment using FormData." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Composer — attach button + staged-file chip + wire into ChatPage + DirectChatPage

**Files:**
- Create: `frontend/src/components/ComposerAttachButton.tsx`
- Create: `frontend/src/components/AttachmentPreviewChip.tsx`
- Modify: `frontend/src/pages/ChatPage.tsx`
- Modify: `frontend/src/pages/DirectChatPage.tsx`

- [ ] **Step 1: `ComposerAttachButton.tsx`**

```tsx
import React, { useRef } from 'react';

interface Props {
  onFile: (file: File) => void;
  disabled?: boolean;
}

const ACCEPT = 'image/png,image/jpeg,image/gif,image/webp,application/pdf,text/plain,application/zip';

export const ComposerAttachButton: React.FC<Props> = ({ onFile, disabled }) => {
  const inputRef = useRef<HTMLInputElement>(null);

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) onFile(f);
    // Reset so picking the same filename again re-fires change
    e.target.value = '';
  };

  return (
    <>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled}
        className="px-2 py-1 text-lg hover:bg-gray-100 rounded disabled:opacity-50"
        aria-label="Attach file"
      >
        📎
      </button>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        onChange={onChange}
        className="hidden"
      />
    </>
  );
};
```

- [ ] **Step 2: `AttachmentPreviewChip.tsx`**

```tsx
import React from 'react';
import { prettySize } from '../types/attachment';

interface Props {
  file: File;
  onRemove: () => void;
}

export const AttachmentPreviewChip: React.FC<Props> = ({ file, onRemove }) => (
  <div className="flex items-center gap-2 bg-gray-100 border border-gray-300 rounded px-3 py-1 text-xs">
    <span className="truncate max-w-xs">
      📎 {file.name} · <span className="text-gray-500">{prettySize(file.size)}</span>
    </span>
    <button
      onClick={onRemove}
      className="ml-auto text-gray-500 hover:text-gray-700"
      aria-label="Remove attachment"
      type="button"
    >
      ×
    </button>
  </div>
);
```

- [ ] **Step 3: Wire into `ChatPage.tsx`**

At the top of the file, add:

```tsx
import { ComposerAttachButton } from '../components/ComposerAttachButton';
import { AttachmentPreviewChip } from '../components/AttachmentPreviewChip';
```

Add state near the existing `replyTarget` state:

```tsx
const [stagedFile, setStagedFile] = useState<File | null>(null);
```

Replace the current `handleSend` with a variant that picks REST vs WS:

```tsx
const handleSend = async (text: string) => {
  if (!roomId) return;
  if (stagedFile) {
    try {
      await messageService.sendMessageWithAttachment(roomId, text, stagedFile, replyTarget?.id);
      setStagedFile(null);
      setReplyTarget(null);
    } catch (err) {
      console.error('Failed to send message with attachment:', err);
    }
    return;
  }
  if (!isConnected) return;
  try {
    sendWebSocketMessage(roomId, text, replyTarget?.id);
    setReplyTarget(null);
  } catch (err) {
    console.error('Failed to send message:', err);
  }
};
```

Find the existing `actions={…}` prop on `<MessageInput>` and update it to include the attach button and optional preview chip:

```tsx
actions={
  <>
    <ComposerAttachButton onFile={(f) => setStagedFile(f)} disabled={!!stagedFile} />
    <EmojiPickerButton onPick={(e) => inputRef.current?.insertText(e)} />
    {stagedFile && (
      <AttachmentPreviewChip file={stagedFile} onRemove={() => setStagedFile(null)} />
    )}
    {replyPreview && (
      <ReplyPill
        authorUsername={replyPreview.authorUsername}
        textPreview={replyPreview.textPreview}
        onDismiss={() => setReplyTarget(null)}
      />
    )}
  </>
}
```

The `disabled` check on `<MessageInput>` should also allow send when a file is staged even if WS isn't connected — change `disabled={!isConnected}` to `disabled={!isConnected && !stagedFile}`.

- [ ] **Step 4: Wire into `DirectChatPage.tsx`**

Import the two new components + `directMessageService`. Add `stagedFile` state. Replace `handleSend` to branch on stagedFile (REST) vs WS same way. Extend the adapter that turns `DirectMessage` → shared `Message` to include `attachment: m.attachment` (one extra line — the current adapter already maps other fields).

Also update the `actions={…}` block on `<MessageInput>` same way as `ChatPage.tsx`.

- [ ] **Step 5: Build + vitest**

```bash
cd /src/ai_hakaton/frontend && npm run build && npm test -- --run
```

Expected: clean + green. The existing vitest suites don't exercise attachment UI so they should stay green. Fix narrowly if anything fails.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/components/ComposerAttachButton.tsx \
        frontend/src/components/AttachmentPreviewChip.tsx \
        frontend/src/pages/ChatPage.tsx \
        frontend/src/pages/DirectChatPage.tsx
git commit -m "feat(frontend): composer attach button + staged-file chip + REST send path" -m "ComposerAttachButton opens a hidden file input with a MIME accept allow-list. AttachmentPreviewChip renders the selected file with a remove ×. ChatPage + DirectChatPage stage the file; when present, sending uses the new REST multipart endpoint; otherwise WS path unchanged. Reply pill + emoji picker still compose in the same actions slot." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: `MessageItem` renders inline image / download link

**Files:**
- Create: `frontend/src/components/AttachmentRenderer.tsx`
- Modify: `frontend/src/components/MessageItem.tsx`

- [ ] **Step 1: `AttachmentRenderer.tsx`**

```tsx
import React from 'react';
import type { AttachmentSummary } from '../types/attachment';
import { isImageMime, prettySize } from '../types/attachment';

interface Props {
  attachment: AttachmentSummary;
}

export const AttachmentRenderer: React.FC<Props> = ({ attachment }) => {
  const url = `/api/attachments/${attachment.id}/content`;
  if (isImageMime(attachment.mimeType)) {
    return (
      <a href={url} target="_blank" rel="noopener noreferrer" className="block mt-2">
        <img
          src={url}
          alt={attachment.filename}
          className="max-h-80 max-w-full rounded border"
        />
      </a>
    );
  }
  return (
    <a
      href={url}
      download={attachment.filename}
      className="inline-flex items-center gap-2 mt-2 text-sm text-blue-600 hover:underline"
    >
      📎 {attachment.filename} · <span className="text-gray-500">{prettySize(attachment.sizeBytes)}</span>
    </a>
  );
};
```

- [ ] **Step 2: Use it in `MessageItem.tsx`**

At the top, import:

```tsx
import { AttachmentRenderer } from './AttachmentRenderer';
```

Inside the non-tombstone message body JSX, below the `<p>` that shows `message.text` but outside the `InlineMessageEditor` branch, render:

```tsx
{message.attachment && <AttachmentRenderer attachment={message.attachment} />}
```

It should appear after the existing reactions chips (if any) — place it at the bottom of the bubble. The component returns `null` when `message.attachment` is undefined (because the JSX conditional filters that case).

If a message has empty text AND an attachment, the `<p>` that shows `message.text` is still there but renders as an empty paragraph — add a guard so it doesn't render:

```tsx
{message.text && <p className="text-gray-700 mt-1 whitespace-pre-wrap">{message.text}</p>}
```

(Replace the existing unconditional `<p>` with this guarded version.)

- [ ] **Step 3: Build + vitest**

```bash
cd /src/ai_hakaton/frontend && npm run build && npm test -- --run
```

Expected: clean + green.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/components/AttachmentRenderer.tsx \
        frontend/src/components/MessageItem.tsx
git commit -m "feat(frontend): MessageItem renders AttachmentRenderer (inline image / download link)" -m "Images render as a clickable thumbnail (max-h-80, opens full size in a new tab). Non-images render as a download link with size. MessageItem only renders the message text paragraph when non-empty, so file-only messages don't leave a blank line." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Playwright E2E — `attachments.spec.ts`

**Files:**
- Create: `frontend/e2e/attachments.spec.ts`

- [ ] **Step 1: Rebuild the backend container (V8 + MinIO)**

```bash
cd /src/ai_hakaton && docker compose up -d --build
sleep 15
docker logs chat-backend --tail 10 2>&1 | grep -E 'Started|ERROR|V8'
docker logs chat-minio --tail 3 2>&1
```

Expected: backend logs show "Started ChatServerApplication" and the V8 migration applied; MinIO logs show it's ready on port 9000. No errors.

- [ ] **Step 2: Create the spec**

```typescript
import { test, expect, Browser, Page } from '@playwright/test';
import path from 'path';
import fs from 'fs';
import os from 'os';

const password = 'password12345';

function uniqueUser(prefix: string) {
  const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
  const username = `${prefix}${stamp}`;
  return { username, email: `${username}@example.com`, password };
}

async function registerAndLogin(
  browser: Browser,
  email: string,
  username: string,
  pw: string,
): Promise<{ ctx: Awaited<ReturnType<Browser['newContext']>>; page: Page }> {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto('/register');
  await page.fill('#email', email);
  await page.fill('#username', username);
  await page.fill('#password', pw);
  await page.fill('#confirm-password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/login$/);
  await page.fill('#email', email);
  await page.fill('#password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/rooms$/);
  return { ctx, page };
}

// 1x1 transparent PNG
const tinyPngBase64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

function writeTempPng(): string {
  const file = path.join(os.tmpdir(), `tiny-${Date.now()}.png`);
  fs.writeFileSync(file, Buffer.from(tinyPngBase64, 'base64'));
  return file;
}

test.describe('Attachments', () => {
  test('image upload in a room is visible to both clients', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const bob = uniqueUser('bob');
    const { ctx: aCtx, page: aPage } = await registerAndLogin(
      browser, alice.email, alice.username, alice.password);

    // Alice creates a room
    const roomName = `att-${Date.now().toString().slice(-7)}`;
    await aPage.click('button:has-text("New Room")');
    await aPage.fill('input[placeholder="Enter room name"]', roomName);
    await aPage.click('div.fixed button:has-text("Create"):not(:has-text("Cancel"))');
    await aPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    const roomUrl = aPage.url();

    // Attach a tiny PNG and send
    const tmpFile = writeTempPng();
    await aPage.locator('input[type="file"]').setInputFiles(tmpFile);
    // Preview chip appears
    await expect(aPage.locator('body')).toContainText('tiny-');
    await aPage.getByPlaceholder(/type a message/i).fill('here is a pic');
    await aPage.keyboard.press('Control+Enter');
    // Alice sees her own message with an <img>
    await expect(aPage.locator('img[alt*="tiny-"]').first()).toBeVisible({ timeout: 5_000 });

    // Bob joins and sees the image too
    const { ctx: bCtx, page: bPage } = await registerAndLogin(
      browser, bob.email, bob.username, bob.password);
    await bPage.goto(roomUrl);
    await bPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await expect(bPage.locator('img[alt*="tiny-"]').first()).toBeVisible({ timeout: 5_000 });

    fs.unlinkSync(tmpFile);
    await aCtx.close();
    await bCtx.close();
  });
});
```

- [ ] **Step 3: Run the full Playwright suite**

```bash
cd /src/ai_hakaton/frontend && npm run test:e2e -- --reporter=line
```

Expected: all scenarios green. If the image selector is brittle (e.g. React wraps the `<img>` in a way that changes `alt` handling), narrow the selector in this spec file only.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add frontend/e2e/attachments.spec.ts
git commit -m "test(e2e): attachment upload in a room is visible to both clients" -m "Alice creates a room, attaches a tiny PNG via the file input, sends with Ctrl+Enter. Both her and Bob's clients render the inline <img>." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Roadmap update

**Files:**
- Modify: `FEATURES_ROADMAP.md`

- [ ] **Step 1: Move Feature #6 to Completed**

Find `### Feature #6: Attachments (File & Image Sharing)` under `## Planned Features` and replace with:

```markdown
### Feature #6: Attachments (File & Image Sharing) ✅
- One attachment per chat-room or direct-message (multi-attachment deferred)
- S3-compatible object storage (MinIO in docker-compose; any S3 endpoint in prod — every backend replica sees the same bytes)
- Multipart `POST /api/rooms/{rid}/messages` and `/api/dms/{cid}/messages` accept `text` + `file`; plain text still goes over WebSocket
- Backend-proxied `GET /api/attachments/{id}/content` with room-membership / DM-participation auth; `Content-Disposition: inline` for image/* allow-list, `attachment` for everything else
- 10 MB cap; MIME allow-list — png/jpeg/gif/webp/pdf/txt/zip (SVG / HTML / executables rejected)
- Soft-delete of a message unlinks the S3 object; hard cascade (room/conversation delete) removes DB rows via FK CASCADE (S3 objects may orphan on hard cascade — known limitation)
- Frontend: `ComposerAttachButton` + `AttachmentPreviewChip` + `AttachmentRenderer` (inline image or download link); `MessageItem` renders either
- Backend tests: `InMemoryStorageServiceTest`, `AttachmentPolicyTest`, `AttachmentControllerTest`, `AttachmentFlowIntegrationTest`
- Playwright: `attachments.spec.ts` — two-client image upload lifecycle
- Spec: `docs/superpowers/specs/2026-04-19-attachments-design.md`
- Plan: `docs/superpowers/plans/2026-04-19-attachments.md` (12 tasks — all complete)
- **Status: COMPLETE**
```

Move this block into `## Completed Features` below the Feature #8 entry.

- [ ] **Step 2: Update the Progress block**

Replace:

```markdown
## Progress
- **Completed:** 7 execution slots (Features #1, #2, #3, #4, App Shell Refactor, Message Content, Account Management) + polish (emoji picker + reactions, chat ordering)
- **In progress:** 0
- **Remaining:** 3 (Attachments, Presence/Sessions, Password Reset)
```

with:

```markdown
## Progress
- **Completed:** 8 execution slots (Features #1, #2, #3, #4, App Shell Refactor, Message Content, Account Management, Attachments) + polish (emoji picker + reactions, chat ordering)
- **In progress:** 0
- **Remaining:** 3 (Presence/Sessions, Password Reset, YouTube Embeds)
```

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add FEATURES_ROADMAP.md
git commit -m "docs(roadmap): Feature #6 (attachments) complete" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Verification Checklist

Before considering Feature #6 shipped:

- [ ] V8 migration applies cleanly on a fresh `docker compose up -d --build` (backend logs "Successfully applied 1 migration ... v8")
- [ ] MinIO container starts; backend creates `chat-attachments` bucket on boot (logs "Creating storage bucket chat-attachments")
- [ ] `./gradlew test` — full backend suite green
- [ ] `npm run build` clean, `npm test -- --run` green
- [ ] `npm run test:e2e` — Playwright green (existing 15 + new)
- [ ] Browser smoke:
  - Upload a PNG in a room — two tabs both see the inline `<img>`
  - Upload a PDF in a DM — other side sees a download link, click downloads the file
  - Attempt a >10MB file upload — UI shows an error (413 from Spring)
- [ ] `FEATURES_ROADMAP.md` reflects Feature #6 COMPLETE
