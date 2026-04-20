# Server-Side Embed Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist YouTube embed metadata (`kind`, `canonical_id`, `source_url`, `title`, `thumbnail_url`) server-side on message send + edit, backfill existing messages, and expose the data on outbound DTOs so the frontend renders cached titles/thumbnails instead of re-running regex on every render.

**Architecture:** New `message_embeds` + `direct_message_embeds` tables (CASCADE on message delete, UNIQUE per message + canonical_id). A pure `YouTubeUrlExtractor` mirrors the frontend regex. `YouTubeOEmbedClient` calls YouTube's oEmbed endpoint synchronously with a 1.5 s timeout and returns `Optional.empty()` on any failure. `EmbedService` is called from `MessageService` / `DirectMessageService` on create + edit; a one-off V13 Flyway Java migration backfills existing rows. DTOs gain a `List<EmbedDto> embeds` field consumed by a reshaped `YouTubeEmbed` component on the frontend (regex fallback for pre-backfill rows as a safety net).

**Tech Stack:** Spring Boot 3.5 / Java 25 / Lombok / Spring 6 `RestClient` / MockWebServer / Flyway (SQL + Java migrations) / Postgres 15 / React 19 + TypeScript / Vitest / Playwright.

---

## File Structure

### Backend

**New:**
- `backend/src/main/resources/db/migration/V12__message_embeds.sql`
- `backend/src/main/java/com/hackathon/features/messages/embeds/MessageEmbed.java`
- `backend/src/main/java/com/hackathon/features/messages/embeds/MessageEmbedRepository.java`
- `backend/src/main/java/com/hackathon/features/messages/embeds/DirectMessageEmbed.java`
- `backend/src/main/java/com/hackathon/features/messages/embeds/DirectMessageEmbedRepository.java`
- `backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractor.java`
- `backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClient.java`
- `backend/src/main/java/com/hackathon/features/messages/embeds/EmbedService.java`
- `backend/src/main/java/com/hackathon/shared/dto/EmbedDto.java`
- `backend/src/main/java/com/hackathon/shared/config/RestClientConfig.java`
- `backend/src/main/resources/db/migration/V13__backfill_embeds.java`
- `backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractorTest.java`
- `backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClientTest.java`
- `backend/src/test/java/com/hackathon/features/messages/embeds/EmbedServiceTest.java`
- `backend/src/test/java/com/hackathon/features/messages/embeds/EmbedFlowIntegrationTest.java`

**Modified:**
- `backend/src/main/java/com/hackathon/features/messages/MessageService.java` (hook embed service into send + edit; include embeds in `toDto`)
- `backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java` (same)
- `backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java` (add `embeds` field)
- `backend/src/main/java/com/hackathon/shared/dto/DirectMessageDTO.java` (add `embeds` field)
- `backend/src/main/resources/application.yml` (add `app.oembed.youtube-url`)
- `backend/src/test/resources/application-test.yml` (point oEmbed URL at placeholder, overridden in tests)
- `backend/build.gradle` (add `mockwebserver` test dependency if absent)

### Frontend

**New:**
- `frontend/src/components/__tests__/YouTubeEmbed.test.tsx`

**Modified:**
- `frontend/src/types/room.ts` (add `Embed` interface + `embeds` on `Message`)
- `frontend/src/types/directMessage.ts` (add `embeds` on `DirectMessage`)
- `frontend/src/components/YouTubeEmbed.tsx` (accept `embeds`, render title/thumbnail from DTO, fall back to regex)
- `frontend/src/components/MessageItem.tsx` (pass `message.embeds` to YouTubeEmbed)
- `frontend/e2e/chat-layout.spec.ts` (extend with one YouTube-embed assertion)

### Roadmap

- `FEATURES_ROADMAP.md` — mark Feature #11 complete on the final task.

---

## Task 1: Schema — V12 migration + entities + repositories

**Goal:** Ship the two tables, JPA entities, and Spring Data repositories. No service code yet. After this task the DB has empty `message_embeds` / `direct_message_embeds` tables and new rows can be inserted through repositories.

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__message_embeds.sql`
- Create: `backend/src/main/java/com/hackathon/features/messages/embeds/MessageEmbed.java`
- Create: `backend/src/main/java/com/hackathon/features/messages/embeds/MessageEmbedRepository.java`
- Create: `backend/src/main/java/com/hackathon/features/messages/embeds/DirectMessageEmbed.java`
- Create: `backend/src/main/java/com/hackathon/features/messages/embeds/DirectMessageEmbedRepository.java`

- [ ] **Step 1.1: Write the migration**

Create `backend/src/main/resources/db/migration/V12__message_embeds.sql`:

```sql
CREATE TABLE message_embeds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    canonical_id VARCHAR(64) NOT NULL,
    source_url TEXT NOT NULL,
    title TEXT NULL,
    thumbnail_url TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (message_id, canonical_id)
);
CREATE INDEX idx_message_embeds_canonical ON message_embeds(canonical_id);
CREATE INDEX idx_message_embeds_message ON message_embeds(message_id);

CREATE TABLE direct_message_embeds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    direct_message_id UUID NOT NULL REFERENCES direct_messages(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    canonical_id VARCHAR(64) NOT NULL,
    source_url TEXT NOT NULL,
    title TEXT NULL,
    thumbnail_url TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (direct_message_id, canonical_id)
);
CREATE INDEX idx_dm_embeds_canonical ON direct_message_embeds(canonical_id);
CREATE INDEX idx_dm_embeds_message ON direct_message_embeds(direct_message_id);
```

- [ ] **Step 1.2: Create the `MessageEmbed` entity**

Create `backend/src/main/java/com/hackathon/features/messages/embeds/MessageEmbed.java`:

```java
package com.hackathon.features.messages.embeds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "message_embeds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEmbed {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "canonical_id", nullable = false, length = 64)
    private String canonicalId;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
```

- [ ] **Step 1.3: Create the `DirectMessageEmbed` entity**

Create `backend/src/main/java/com/hackathon/features/messages/embeds/DirectMessageEmbed.java`:

```java
package com.hackathon.features.messages.embeds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "direct_message_embeds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageEmbed {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "direct_message_id", nullable = false)
    private UUID directMessageId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "canonical_id", nullable = false, length = 64)
    private String canonicalId;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
```

- [ ] **Step 1.4: Create the repositories**

Create `backend/src/main/java/com/hackathon/features/messages/embeds/MessageEmbedRepository.java`:

```java
package com.hackathon.features.messages.embeds;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageEmbedRepository extends JpaRepository<MessageEmbed, UUID> {

    List<MessageEmbed> findByMessageId(UUID messageId);

    List<MessageEmbed> findByMessageIdIn(Collection<UUID> messageIds);

    @Modifying
    @Query("delete from MessageEmbed e where e.messageId = :messageId and e.canonicalId not in :keep")
    void deleteByMessageIdAndCanonicalIdNotIn(
            @Param("messageId") UUID messageId, @Param("keep") Collection<String> keep);

    @Modifying
    @Query("delete from MessageEmbed e where e.messageId = :messageId")
    void deleteByMessageId(@Param("messageId") UUID messageId);
}
```

Create `backend/src/main/java/com/hackathon/features/messages/embeds/DirectMessageEmbedRepository.java`:

```java
package com.hackathon.features.messages.embeds;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageEmbedRepository extends JpaRepository<DirectMessageEmbed, UUID> {

    List<DirectMessageEmbed> findByDirectMessageId(UUID directMessageId);

    List<DirectMessageEmbed> findByDirectMessageIdIn(Collection<UUID> directMessageIds);

    @Modifying
    @Query("delete from DirectMessageEmbed e "
         + "where e.directMessageId = :dmId and e.canonicalId not in :keep")
    void deleteByDirectMessageIdAndCanonicalIdNotIn(
            @Param("dmId") UUID dmId, @Param("keep") Collection<String> keep);

    @Modifying
    @Query("delete from DirectMessageEmbed e where e.directMessageId = :dmId")
    void deleteByDirectMessageId(@Param("dmId") UUID dmId);
}
```

- [ ] **Step 1.5: Build the project to verify migration + entities compile & apply**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`

Run: `cd backend && ./gradlew test --tests 'com.hackathon.ChatApplicationTests'`
Expected: `BUILD SUCCESSFUL` — the context loads with the new entities and the V12 migration applies against the testcontainer Postgres.

- [ ] **Step 1.6: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__message_embeds.sql \
        backend/src/main/java/com/hackathon/features/messages/embeds/
git commit -m "$(cat <<'EOF'
feat(embeds): V12 schema + MessageEmbed / DirectMessageEmbed entities

Adds the two tables with FK CASCADE on the owning message row and a
UNIQUE(message_id, canonical_id) constraint so repeated URLs in a single
message dedupe. No service wiring yet — empty tables are harmless.
EOF
)"
```

---

## Task 2: `YouTubeUrlExtractor` — pure function mirroring frontend regex

**Goal:** A stateless utility class that returns a list of `Extracted` records (kind, canonical ID, source URL) in encounter order, deduped by canonical ID. Identical behavior to `frontend/src/utils/youtube.ts::extractYouTubeIds` for watch, youtu.be, shorts, and embed URL shapes. No Spring, no DB, easy to unit-test.

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractor.java`
- Create: `backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractorTest.java`

- [ ] **Step 2.1: Write the failing tests**

Create `backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractorTest.java`:

```java
package com.hackathon.features.messages.embeds;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class YouTubeUrlExtractorTest {

    @Test
    void extractsWatchUrl() {
        var hits = YouTubeUrlExtractor.extract("check this https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo("youtube");
        assertThat(hits.get(0).canonicalId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(hits.get(0).sourceUrl()).contains("dQw4w9WgXcQ");
    }

    @Test
    void extractsShortUrl() {
        var hits = YouTubeUrlExtractor.extract("https://youtu.be/dQw4w9WgXcQ?t=30");
        assertThat(hits).singleElement().satisfies(h ->
                assertThat(h.canonicalId()).isEqualTo("dQw4w9WgXcQ"));
    }

    @Test
    void extractsShortsUrl() {
        var hits = YouTubeUrlExtractor.extract("look at https://www.youtube.com/shorts/abcdefghijk end");
        assertThat(hits).singleElement().satisfies(h ->
                assertThat(h.canonicalId()).isEqualTo("abcdefghijk"));
    }

    @Test
    void extractsEmbedUrl() {
        var hits = YouTubeUrlExtractor.extract("https://www.youtube.com/embed/abcdefghijk");
        assertThat(hits).singleElement().satisfies(h ->
                assertThat(h.canonicalId()).isEqualTo("abcdefghijk"));
    }

    @Test
    void handlesWatchUrlWithExtraParams() {
        var hits = YouTubeUrlExtractor.extract("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share&t=30");
        assertThat(hits).singleElement().satisfies(h ->
                assertThat(h.canonicalId()).isEqualTo("dQw4w9WgXcQ"));
    }

    @Test
    void preservesEncounterOrderAndDedupes() {
        String text = "first https://youtu.be/AAAAAAAAAAA then "
                    + "https://www.youtube.com/watch?v=BBBBBBBBBBB and again "
                    + "https://youtu.be/AAAAAAAAAAA done";
        var hits = YouTubeUrlExtractor.extract(text);
        assertThat(hits).extracting(YouTubeUrlExtractor.Extracted::canonicalId)
                .containsExactly("AAAAAAAAAAA", "BBBBBBBBBBB");
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertThat(YouTubeUrlExtractor.extract(null)).isEmpty();
        assertThat(YouTubeUrlExtractor.extract("")).isEmpty();
        assertThat(YouTubeUrlExtractor.extract("   ")).isEmpty();
    }

    @Test
    void ignoresNonYouTubeLinks() {
        var hits = YouTubeUrlExtractor.extract(
                "https://example.com/watch?v=notyoutube https://vimeo.com/123456");
        assertThat(hits).isEmpty();
    }

    @Test
    void handlesMultipleDistinctVideos() {
        var hits = YouTubeUrlExtractor.extract(
                "https://youtu.be/AAAAAAAAAAA https://www.youtube.com/shorts/BBBBBBBBBBB "
                        + "https://www.youtube.com/embed/CCCCCCCCCCC");
        assertThat(hits).extracting(YouTubeUrlExtractor.Extracted::canonicalId)
                .containsExactly("AAAAAAAAAAA", "BBBBBBBBBBB", "CCCCCCCCCCC");
    }
}
```

- [ ] **Step 2.2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.YouTubeUrlExtractorTest'`
Expected: `FAILED` with "YouTubeUrlExtractor cannot be resolved" / compile error.

- [ ] **Step 2.3: Write the extractor**

Create `backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractor.java`:

```java
package com.hackathon.features.messages.embeds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure utility — mirrors frontend/src/utils/youtube.ts::extractYouTubeIds. */
public final class YouTubeUrlExtractor {

    public record Extracted(String kind, String canonicalId, String sourceUrl) {}

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile(
                    "(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?(?:[^\\s\"'<>]*&)?"
                            + "v=([a-zA-Z0-9_-]{11})(?:[^a-zA-Z0-9_-][^\\s\"'<>]*)?"),
            Pattern.compile(
                    "(?:https?://)?(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})(?:[?#&][^\\s\"'<>]*)?"),
            Pattern.compile(
                    "(?:https?://)?(?:www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})"
                            + "(?:[?#&][^\\s\"'<>]*)?"),
            Pattern.compile(
                    "(?:https?://)?(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})"
                            + "(?:[?#&][^\\s\"'<>]*)?"));

    private YouTubeUrlExtractor() {}

    public static List<Extracted> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        record Hit(String canonicalId, String sourceUrl, int index) {}
        List<Hit> hits = new ArrayList<>();
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                hits.add(new Hit(m.group(1), m.group(), m.start()));
            }
        }
        hits.sort(Comparator.comparingInt(Hit::index));
        Set<String> seen = new HashSet<>();
        List<Extracted> out = new ArrayList<>();
        for (Hit h : hits) {
            if (seen.add(h.canonicalId())) {
                out.add(new Extracted("youtube", h.canonicalId(), h.sourceUrl()));
            }
        }
        return out;
    }
}
```

- [ ] **Step 2.4: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.YouTubeUrlExtractorTest'`
Expected: `BUILD SUCCESSFUL` — 9 passed.

- [ ] **Step 2.5: Commit**

```bash
git add backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractor.java \
        backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeUrlExtractorTest.java
git commit -m "$(cat <<'EOF'
feat(embeds): YouTubeUrlExtractor mirrors frontend regex

Same URL shapes (watch, youtu.be, shorts, embed), same dedup + encounter
order. 9 unit tests cover each shape plus null / blank / non-YT / mixed.
EOF
)"
```

---

## Task 3: `YouTubeOEmbedClient` — sync HTTP fetch with 1.5 s timeout

**Goal:** A Spring bean that calls YouTube's oEmbed endpoint (`https://www.youtube.com/oembed`) and returns `Optional<OEmbedData>`. Any failure — timeout, non-2xx, parse error — returns `Optional.empty()`. Base URL comes from configuration so tests can point at a MockWebServer.

**Files:**
- Create: `backend/src/main/java/com/hackathon/shared/config/RestClientConfig.java`
- Create: `backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClient.java`
- Create: `backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClientTest.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`
- Modify: `backend/build.gradle`

- [ ] **Step 3.1: Add MockWebServer test dependency**

In `backend/build.gradle`, add inside the `dependencies { ... }` block under the other `testImplementation` lines:

```groovy
    testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
```

Run: `cd backend && ./gradlew dependencies --configuration testRuntimeClasspath > /dev/null`
Expected: exit 0. If the dependency was already present, the line is a harmless duplicate — keep the version aligned with whatever is already there.

- [ ] **Step 3.2: Add the oEmbed base URL config**

In `backend/src/main/resources/application.yml`, append under the top-level `app:` block (merge; don't duplicate `app:`):

```yaml
app:
  oembed:
    youtube-url: ${APP_OEMBED_YOUTUBE_URL:https://www.youtube.com/oembed}
```

In `backend/src/test/resources/application-test.yml`, append:

```yaml
app:
  oembed:
    # Placeholder — tests set the real URL via @DynamicPropertySource.
    youtube-url: http://localhost:0/oembed
```

- [ ] **Step 3.3: Create a small `RestClient` factory config**

Create `backend/src/main/java/com/hackathon/shared/config/RestClientConfig.java`:

```java
package com.hackathon.shared.config;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("oEmbedRestClient")
    RestClient oEmbedRestClient() {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(1500))
                .withReadTimeout(Duration.ofMillis(1500));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
```

- [ ] **Step 3.4: Write the failing tests**

Create `backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClientTest.java`:

```java
package com.hackathon.features.messages.embeds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

class YouTubeOEmbedClientTest {

    private MockWebServer server;
    private YouTubeOEmbedClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(500))
                .withReadTimeout(Duration.ofMillis(500));
        RestClient rc = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
        client = new YouTubeOEmbedClient(rc, server.url("/oembed").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void returnsDataOn200() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"title\":\"Rick Astley\",\"thumbnail_url\":\"https://img/x.jpg\"}"));
        Optional<YouTubeOEmbedClient.OEmbedData> out =
                client.fetch("https://youtu.be/dQw4w9WgXcQ");
        assertThat(out).isPresent();
        assertThat(out.get().title()).isEqualTo("Rick Astley");
        assertThat(out.get().thumbnailUrl()).isEqualTo("https://img/x.jpg");
    }

    @Test
    void returnsEmptyOn404() {
        server.enqueue(new MockResponse().setResponseCode(404));
        assertThat(client.fetch("https://youtu.be/nope")).isEmpty();
    }

    @Test
    void returnsEmptyOn429() {
        server.enqueue(new MockResponse().setResponseCode(429));
        assertThat(client.fetch("https://youtu.be/rate")).isEmpty();
    }

    @Test
    void returnsEmptyOnTimeout() {
        server.enqueue(new MockResponse()
                .setBody("{}")
                .setHeadersDelay(2, java.util.concurrent.TimeUnit.SECONDS));
        assertThat(client.fetch("https://youtu.be/slow")).isEmpty();
    }

    @Test
    void returnsEmptyOnMalformedJson() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("not-json"));
        assertThat(client.fetch("https://youtu.be/broken")).isEmpty();
    }
}
```

- [ ] **Step 3.5: Run the tests to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.YouTubeOEmbedClientTest'`
Expected: compile error — `YouTubeOEmbedClient` does not exist.

- [ ] **Step 3.6: Write the client**

Create `backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClient.java`:

```java
package com.hackathon.features.messages.embeds;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class YouTubeOEmbedClient {

    public record OEmbedData(String title, String thumbnailUrl) {}

    private record OEmbedResponse(
            String title,
            @JsonProperty("thumbnail_url") String thumbnailUrl) {}

    private final RestClient restClient;
    private final String endpoint;

    public YouTubeOEmbedClient(
            @Qualifier("oEmbedRestClient") RestClient restClient,
            @Value("${app.oembed.youtube-url}") String endpoint) {
        this.restClient = restClient;
        this.endpoint = endpoint;
    }

    /** Returns Optional.empty() on any failure — never throws. */
    public Optional<OEmbedData> fetch(String sourceUrl) {
        String url = UriComponentsBuilder.fromHttpUrl(endpoint)
                .queryParam("url", sourceUrl)
                .queryParam("format", "json")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        try {
            OEmbedResponse resp = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(OEmbedResponse.class);
            if (resp == null) return Optional.empty();
            return Optional.of(new OEmbedData(resp.title(), resp.thumbnailUrl()));
        } catch (Exception e) {
            log.debug("oEmbed fetch failed for {}: {}", sourceUrl, e.toString());
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 3.7: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.YouTubeOEmbedClientTest'`
Expected: `BUILD SUCCESSFUL` — 5 passed.

- [ ] **Step 3.8: Commit**

```bash
git add backend/build.gradle \
        backend/src/main/resources/application.yml \
        backend/src/test/resources/application-test.yml \
        backend/src/main/java/com/hackathon/shared/config/RestClientConfig.java \
        backend/src/main/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClient.java \
        backend/src/test/java/com/hackathon/features/messages/embeds/YouTubeOEmbedClientTest.java
git commit -m "$(cat <<'EOF'
feat(embeds): YouTubeOEmbedClient (sync, 1.5 s timeout, best-effort)

Spring RestClient bean with configurable base URL (app.oembed.youtube-url)
so MockWebServer-backed tests can stub the endpoint. Returns
Optional.empty() on any failure so callers can persist nulls without
failing the send path.
EOF
)"
```

---

## Task 4: `EmbedService` + wiring into room messages

**Goal:** Implement `EmbedService` with `persistForMessage` (called from `MessageService.sendMessage`) and `reconcileForMessage` (called from `MessageService.editMessage`). Leave DMs for Task 5 and backfill for Task 6.

**Files:**
- Create: `backend/src/main/java/com/hackathon/features/messages/embeds/EmbedService.java`
- Create: `backend/src/test/java/com/hackathon/features/messages/embeds/EmbedServiceTest.java`
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageService.java`

- [ ] **Step 4.1: Write the failing tests**

Create `backend/src/test/java/com/hackathon/features/messages/embeds/EmbedServiceTest.java`:

```java
package com.hackathon.features.messages.embeds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hackathon.features.messages.Message;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbedServiceTest {

    @Mock MessageEmbedRepository messageEmbedRepo;
    @Mock DirectMessageEmbedRepository dmEmbedRepo;
    @Mock YouTubeOEmbedClient oEmbedClient;
    @InjectMocks EmbedService embedService;

    private Message msgWith(String text) {
        return Message.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .text(text)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void persistForMessage_noYouTubeLinks_doesNothing() {
        embedService.persistForMessage(msgWith("hello world"));
        verifyNoInteractions(oEmbedClient, messageEmbedRepo);
    }

    @Test
    void persistForMessage_storesOneRowPerDistinctVideo_withOEmbedMetadata() {
        Message m = msgWith("https://youtu.be/AAAAAAAAAAA and https://youtu.be/BBBBBBBBBBB");
        when(oEmbedClient.fetch(any()))
                .thenReturn(Optional.of(new YouTubeOEmbedClient.OEmbedData("T1", "http://img/1")))
                .thenReturn(Optional.of(new YouTubeOEmbedClient.OEmbedData("T2", "http://img/2")));

        embedService.persistForMessage(m);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(messageEmbedRepo, times(2)).save(captor.capture());
        List<MessageEmbed> saved = captor.getAllValues();
        org.assertj.core.api.Assertions.assertThat(saved)
                .extracting(MessageEmbed::getCanonicalId, MessageEmbed::getTitle, MessageEmbed::getThumbnailUrl)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AAAAAAAAAAA", "T1", "http://img/1"),
                        org.assertj.core.groups.Tuple.tuple("BBBBBBBBBBB", "T2", "http://img/2"));
    }

    @Test
    void persistForMessage_oEmbedFailureStillPersistsRow_withNulls() {
        Message m = msgWith("https://youtu.be/AAAAAAAAAAA");
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());

        embedService.persistForMessage(m);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(messageEmbedRepo).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTitle()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getThumbnailUrl()).isNull();
    }

    @Test
    void reconcileForMessage_removesVanishedVideos_andInsertsNew() {
        Message m = msgWith("now only https://youtu.be/CCCCCCCCCCC");
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());

        embedService.reconcileForMessage(m);

        verify(messageEmbedRepo).deleteByMessageIdAndCanonicalIdNotIn(
                m.getId(), List.of("CCCCCCCCCCC"));
        verify(messageEmbedRepo).save(any(MessageEmbed.class));
    }

    @Test
    void reconcileForMessage_noLinksLeft_deletesEverything() {
        Message m = msgWith("plain edit no urls");
        embedService.reconcileForMessage(m);
        verify(messageEmbedRepo).deleteByMessageId(m.getId());
        verifyNoInteractions(oEmbedClient);
    }
}
```

- [ ] **Step 4.2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.EmbedServiceTest'`
Expected: compile error — `EmbedService` does not exist.

- [ ] **Step 4.3: Implement `EmbedService`**

Create `backend/src/main/java/com/hackathon/features/messages/embeds/EmbedService.java`:

```java
package com.hackathon.features.messages.embeds;

import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.messages.Message;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmbedService {

    private final MessageEmbedRepository messageEmbedRepo;
    private final DirectMessageEmbedRepository dmEmbedRepo;
    private final YouTubeOEmbedClient oEmbedClient;

    @Transactional
    public void persistForMessage(Message m) {
        List<YouTubeUrlExtractor.Extracted> hits = YouTubeUrlExtractor.extract(m.getText());
        for (YouTubeUrlExtractor.Extracted hit : hits) {
            Optional<YouTubeOEmbedClient.OEmbedData> data = oEmbedClient.fetch(hit.sourceUrl());
            messageEmbedRepo.save(MessageEmbed.builder()
                    .messageId(m.getId())
                    .kind(hit.kind())
                    .canonicalId(hit.canonicalId())
                    .sourceUrl(hit.sourceUrl())
                    .title(data.map(YouTubeOEmbedClient.OEmbedData::title).orElse(null))
                    .thumbnailUrl(data.map(YouTubeOEmbedClient.OEmbedData::thumbnailUrl).orElse(null))
                    .build());
        }
    }

    @Transactional
    public void reconcileForMessage(Message m) {
        List<YouTubeUrlExtractor.Extracted> hits = YouTubeUrlExtractor.extract(m.getText());
        if (hits.isEmpty()) {
            messageEmbedRepo.deleteByMessageId(m.getId());
            return;
        }
        List<String> keep = hits.stream().map(YouTubeUrlExtractor.Extracted::canonicalId).toList();
        messageEmbedRepo.deleteByMessageIdAndCanonicalIdNotIn(m.getId(), keep);
        for (YouTubeUrlExtractor.Extracted hit : hits) {
            Optional<YouTubeOEmbedClient.OEmbedData> data = oEmbedClient.fetch(hit.sourceUrl());
            messageEmbedRepo.save(MessageEmbed.builder()
                    .messageId(m.getId())
                    .kind(hit.kind())
                    .canonicalId(hit.canonicalId())
                    .sourceUrl(hit.sourceUrl())
                    .title(data.map(YouTubeOEmbedClient.OEmbedData::title).orElse(null))
                    .thumbnailUrl(data.map(YouTubeOEmbedClient.OEmbedData::thumbnailUrl).orElse(null))
                    .build());
        }
    }

    @Transactional
    public void persistForDirectMessage(DirectMessage m) {
        List<YouTubeUrlExtractor.Extracted> hits = YouTubeUrlExtractor.extract(m.getText());
        for (YouTubeUrlExtractor.Extracted hit : hits) {
            Optional<YouTubeOEmbedClient.OEmbedData> data = oEmbedClient.fetch(hit.sourceUrl());
            dmEmbedRepo.save(DirectMessageEmbed.builder()
                    .directMessageId(m.getId())
                    .kind(hit.kind())
                    .canonicalId(hit.canonicalId())
                    .sourceUrl(hit.sourceUrl())
                    .title(data.map(YouTubeOEmbedClient.OEmbedData::title).orElse(null))
                    .thumbnailUrl(data.map(YouTubeOEmbedClient.OEmbedData::thumbnailUrl).orElse(null))
                    .build());
        }
    }

    @Transactional
    public void reconcileForDirectMessage(DirectMessage m) {
        List<YouTubeUrlExtractor.Extracted> hits = YouTubeUrlExtractor.extract(m.getText());
        if (hits.isEmpty()) {
            dmEmbedRepo.deleteByDirectMessageId(m.getId());
            return;
        }
        List<String> keep = hits.stream().map(YouTubeUrlExtractor.Extracted::canonicalId).toList();
        dmEmbedRepo.deleteByDirectMessageIdAndCanonicalIdNotIn(m.getId(), keep);
        for (YouTubeUrlExtractor.Extracted hit : hits) {
            Optional<YouTubeOEmbedClient.OEmbedData> data = oEmbedClient.fetch(hit.sourceUrl());
            dmEmbedRepo.save(DirectMessageEmbed.builder()
                    .directMessageId(m.getId())
                    .kind(hit.kind())
                    .canonicalId(hit.canonicalId())
                    .sourceUrl(hit.sourceUrl())
                    .title(data.map(YouTubeOEmbedClient.OEmbedData::title).orElse(null))
                    .thumbnailUrl(data.map(YouTubeOEmbedClient.OEmbedData::thumbnailUrl).orElse(null))
                    .build());
        }
    }
}
```

- [ ] **Step 4.4: Run the unit tests to verify they pass**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.EmbedServiceTest'`
Expected: `BUILD SUCCESSFUL` — 5 passed.

- [ ] **Step 4.5: Wire `EmbedService` into `MessageService.sendMessage` + `editMessage`**

In `backend/src/main/java/com/hackathon/features/messages/MessageService.java`:

Add the import at the top:

```java
import com.hackathon.features.messages.embeds.EmbedService;
```

Add the field to the `@RequiredArgsConstructor` block (after `unreadService`):

```java
  private final EmbedService embedService;
```

In the two-arg `sendMessage` (no attachment) — just before `publish(...)` near line 60:

```java
    embedService.persistForMessage(saved);
```

In the multipart `sendMessage` (with attachment) — just before `publish(...)` near line 128:

```java
    embedService.persistForMessage(saved);
```

In `editMessage` — just before `publish(...)` near line 147:

```java
    embedService.reconcileForMessage(saved);
```

- [ ] **Step 4.6: Build**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`.

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.*'`
Expected: `BUILD SUCCESSFUL` — all existing MessageService tests still pass (they don't exercise YouTube text, so oEmbed is never called).

- [ ] **Step 4.7: Commit**

```bash
git add backend/src/main/java/com/hackathon/features/messages/embeds/EmbedService.java \
        backend/src/test/java/com/hackathon/features/messages/embeds/EmbedServiceTest.java \
        backend/src/main/java/com/hackathon/features/messages/MessageService.java
git commit -m "$(cat <<'EOF'
feat(embeds): EmbedService + wiring into MessageService send/edit

persistForMessage runs on create; reconcileForMessage runs on edit and
handles both "link added" and "link removed" cases by deleting rows whose
canonical_id is no longer present. DM path + integration coverage follow.
EOF
)"
```

---

## Task 5: DTO expansion + DM wiring + integration test

**Goal:** Add `List<EmbedDto> embeds` to both DTOs, populate them from the services' `toDto` methods (batched query to avoid N+1), wire `EmbedService` into `DirectMessageService` send + edit, and add a full-stack integration test proving a YT URL round-trips end-to-end.

**Files:**
- Create: `backend/src/main/java/com/hackathon/shared/dto/EmbedDto.java`
- Create: `backend/src/test/java/com/hackathon/features/messages/embeds/EmbedFlowIntegrationTest.java`
- Modify: `backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java`
- Modify: `backend/src/main/java/com/hackathon/shared/dto/DirectMessageDTO.java`
- Modify: `backend/src/main/java/com/hackathon/features/messages/MessageService.java`
- Modify: `backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java`

- [ ] **Step 5.1: Create `EmbedDto`**

Create `backend/src/main/java/com/hackathon/shared/dto/EmbedDto.java`:

```java
package com.hackathon.shared.dto;

import java.util.UUID;

public record EmbedDto(
        UUID id,
        String kind,
        String canonicalId,
        String sourceUrl,
        String title,
        String thumbnailUrl) {}
```

- [ ] **Step 5.2: Add `embeds` to `ChatMessageDTO`**

In `backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java`, add the field just after `attachment`:

```java
  private List<EmbedDto> embeds;
```

Result:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
  private UUID id;
  private UUID roomId;
  private UUID userId;
  private String username;
  private String text;
  private OffsetDateTime createdAt;
  private OffsetDateTime editedAt;
  private OffsetDateTime deletedAt;
  private UUID deletedBy;
  private MessagePreview replyTo;
  private List<ReactionSummary> reactions;
  private AttachmentSummary attachment;
  private List<EmbedDto> embeds;
}
```

- [ ] **Step 5.3: Add `embeds` to `DirectMessageDTO` in the same pattern**

Open `backend/src/main/java/com/hackathon/shared/dto/DirectMessageDTO.java` and add `private List<EmbedDto> embeds;` as the last field. Ensure `import java.util.List;` and `import com.hackathon.shared.dto.EmbedDto;` (same package → no import needed).

- [ ] **Step 5.4: Populate `embeds` in `MessageService.toDto`**

In `backend/src/main/java/com/hackathon/features/messages/MessageService.java`:

Add the import:

```java
import com.hackathon.features.messages.embeds.MessageEmbed;
import com.hackathon.features.messages.embeds.MessageEmbedRepository;
import com.hackathon.shared.dto.EmbedDto;
```

Add the field (after `embedService`):

```java
  private final MessageEmbedRepository messageEmbedRepository;
```

Inside `toDto(Message m, UUID callerId)`, just before the `return ChatMessageDTO.builder()` line, build the list:

```java
    List<EmbedDto> embedDtos = messageEmbedRepository.findByMessageId(m.getId()).stream()
        .map(e -> new EmbedDto(
            e.getId(),
            e.getKind(),
            e.getCanonicalId(),
            e.getSourceUrl(),
            e.getTitle(),
            e.getThumbnailUrl()))
        .toList();
```

Then chain `.embeds(embedDtos)` into the builder.

- [ ] **Step 5.5: Populate `embeds` in `DirectMessageService.toDto` the same way**

Use `DirectMessageEmbedRepository.findByDirectMessageId(m.getId())` and the same mapping.

- [ ] **Step 5.6: Wire `EmbedService` into `DirectMessageService`**

In `DirectMessageService`:

```java
import com.hackathon.features.messages.embeds.EmbedService;
```

Add the field:

```java
  private final EmbedService embedService;
```

In each DM send path (text-only and multipart) just before the `publishToBoth(...)` call, add:

```java
    embedService.persistForDirectMessage(saved);
```

In `editMessage`, before `publishToBoth(...)` for EDITED:

```java
    embedService.reconcileForDirectMessage(saved);
```

- [ ] **Step 5.7: Write the failing integration test**

Create `backend/src/test/java/com/hackathon/features/messages/embeds/EmbedFlowIntegrationTest.java`:

```java
package com.hackathon.features.messages.embeds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.JwtService;
import java.io.IOException;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmbedFlowIntegrationTest {

    private static MockWebServer oEmbedServer;

    @BeforeAll
    static void startMockWebServer() throws IOException {
        oEmbedServer = new MockWebServer();
        oEmbedServer.start();
    }

    @AfterAll
    static void stopMockWebServer() throws IOException {
        oEmbedServer.shutdown();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.oembed.youtube-url", () -> oEmbedServer.url("/oembed").toString());
    }

    @Autowired WebApplicationContext wac;
    @Autowired UserService userService;
    @Autowired ChatRoomService roomService;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    MockMvc mvc;

    @Test
    void sendMessageWithYouTubeUrl_roundTripsEmbed() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Enqueue the oEmbed response the service will hit synchronously during send.
        oEmbedServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"title\":\"Never Gonna Give You Up\","
                        + "\"thumbnail_url\":\"https://img/ricky.jpg\"}"));

        User author = userService.register("embed@test", "embedder", "password123");
        UUID roomId = roomService.createRoom(author.getId(), "e-room", null, "public").getId();
        String token = jwtService.generate(author);

        String body = "{\"text\":\"check https://youtu.be/dQw4w9WgXcQ\"}";
        mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult res = mvc.perform(get("/api/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode msgs = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(msgs.isArray()).isTrue();
        assertThat(msgs.size()).isEqualTo(1);
        JsonNode embeds = msgs.get(0).get("embeds");
        assertThat(embeds).isNotNull();
        assertThat(embeds.size()).isEqualTo(1);
        assertThat(embeds.get(0).get("kind").asText()).isEqualTo("youtube");
        assertThat(embeds.get(0).get("canonicalId").asText()).isEqualTo("dQw4w9WgXcQ");
        assertThat(embeds.get(0).get("title").asText()).isEqualTo("Never Gonna Give You Up");
        assertThat(embeds.get(0).get("thumbnailUrl").asText()).isEqualTo("https://img/ricky.jpg");
    }
}
```

- [ ] **Step 5.8: Run the full embed test package**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.*'`
Expected: `BUILD SUCCESSFUL` — all embed tests green, including the integration round-trip.

- [ ] **Step 5.9: Commit**

```bash
git add backend/src/main/java/com/hackathon/shared/dto/EmbedDto.java \
        backend/src/main/java/com/hackathon/shared/dto/ChatMessageDTO.java \
        backend/src/main/java/com/hackathon/shared/dto/DirectMessageDTO.java \
        backend/src/main/java/com/hackathon/features/messages/MessageService.java \
        backend/src/main/java/com/hackathon/features/dms/DirectMessageService.java \
        backend/src/test/java/com/hackathon/features/messages/embeds/EmbedFlowIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat(embeds): expose embeds on DTOs + DM send/edit wiring + integration

ChatMessageDTO + DirectMessageDTO gain embeds list; services populate
from the embed repos. Round-trip integration test hits MockWebServer
for oEmbed and asserts the DTO carries title + thumbnailUrl.
EOF
)"
```

---

## Task 6: V13 backfill migration

**Goal:** One-off Flyway Java migration that walks every `messages.text` and `direct_messages.text`, runs the extractor, calls oEmbed with a 200 ms throttle, and INSERTs rows with `ON CONFLICT DO NOTHING`. Never fails — per-row oEmbed errors are caught and logged. Uses `JdbcTemplate` directly because Flyway Java migrations run before the Spring context is fully refreshed.

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__backfill_embeds.java`
- Create: `backend/src/test/java/com/hackathon/features/messages/embeds/BackfillMigrationTest.java`

- [ ] **Step 6.1: Write the failing migration test**

Create `backend/src/test/java/com/hackathon/features/messages/embeds/BackfillMigrationTest.java`:

```java
package com.hackathon.features.messages.embeds;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageRepository;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomRepository;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import db.migration.V13__backfill_embeds;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class BackfillMigrationTest {

    static MockWebServer oEmbedServer;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws IOException {
        oEmbedServer = new MockWebServer();
        oEmbedServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"title\":\"T\",\"thumbnail_url\":\"http://img\"}");
            }
        });
        oEmbedServer.start();
        r.add("app.oembed.youtube-url", () -> oEmbedServer.url("/oembed").toString());
    }

    @Autowired Flyway flyway;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired MessageRepository messages;
    @Autowired MessageEmbedRepository embeds;

    @Test
    void backfillInsertsEmbedsForHistoricalMessages() {
        // Insert a user, room, and a message containing a YT link.
        // (Bypass services — we want raw rows, simulating pre-V13 data.)
        User u = users.save(User.builder()
                .email("bf@test")
                .username("bf")
                .passwordHash("x")
                .build());
        ChatRoom r = rooms.save(ChatRoom.builder()
                .name("bf-room").ownerId(u.getId()).visibility("public").build());
        Message m = messages.save(Message.builder()
                .roomId(r.getId()).userId(u.getId())
                .text("watch https://youtu.be/dQw4w9WgXcQ").build());

        // V13 already ran at Spring bootstrap — but the DB was empty then, so no rows were
        // inserted. Manually invoke the migration class against the now-seeded DB.
        embeds.deleteAll();
        var migration = new V13__backfill_embeds();
        org.flywaydb.core.api.migration.Context ctx =
                new org.flywaydb.core.api.migration.Context() {
                    @Override
                    public org.flywaydb.core.api.configuration.Configuration getConfiguration() {
                        return flyway.getConfiguration();
                    }
                    @Override
                    public java.sql.Connection getConnection() {
                        try {
                            return flyway.getConfiguration().getDataSource().getConnection();
                        } catch (java.sql.SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
        try {
            migration.migrate(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<MessageEmbed> rows = embeds.findByMessageId(m.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCanonicalId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(rows.get(0).getTitle()).isEqualTo("T");
    }
}
```

Note: the test invokes the migration class directly (via `Context`) because Flyway otherwise records V13 as applied once during bootstrap and won't re-run it. This exercises the backfill logic deterministically.

- [ ] **Step 6.2: Run the test to verify it fails (compile error — class missing)**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.BackfillMigrationTest'`
Expected: compile error — `V13__backfill_embeds` does not exist.

- [ ] **Step 6.3: Write the migration**

Create `backend/src/main/resources/db/migration/V13__backfill_embeds.java`:

```java
package db.migration;

import com.hackathon.features.messages.embeds.YouTubeOEmbedClient;
import com.hackathon.features.messages.embeds.YouTubeUrlExtractor;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

/**
 * One-off backfill of message_embeds + direct_message_embeds for rows that
 * pre-date V12. Safe to re-run: ON CONFLICT DO NOTHING on (message_id,
 * canonical_id). Never fails per-row oEmbed errors — rows get null
 * title/thumbnail and we move on.
 */
public class V13__backfill_embeds extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V13__backfill_embeds.class);
    private static final String DEFAULT_OEMBED =
            System.getenv().getOrDefault("APP_OEMBED_YOUTUBE_URL", "https://www.youtube.com/oembed");
    private static final long THROTTLE_MS = 200L;

    @Override
    public void migrate(Context ctx) throws Exception {
        Connection cn = ctx.getConnection();
        YouTubeOEmbedClient client = buildClient();

        int roomTotal = backfillTable(
                cn, client,
                "SELECT id, text FROM messages WHERE text IS NOT NULL",
                "INSERT INTO message_embeds "
                        + "(id, message_id, kind, canonical_id, source_url, title, thumbnail_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (message_id, canonical_id) DO NOTHING",
                "message_id");
        int dmTotal = backfillTable(
                cn, client,
                "SELECT id, text FROM direct_messages WHERE text IS NOT NULL",
                "INSERT INTO direct_message_embeds "
                        + "(id, direct_message_id, kind, canonical_id, source_url, title, thumbnail_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (direct_message_id, canonical_id) DO NOTHING",
                "direct_message_id");
        log.info("V13 backfill complete: {} room embeds, {} DM embeds", roomTotal, dmTotal);
    }

    private int backfillTable(
            Connection cn, YouTubeOEmbedClient client, String selectSql, String insertSql,
            String fkColumn) throws Exception {
        int count = 0;
        try (Statement s = cn.createStatement();
             ResultSet rs = s.executeQuery(selectSql);
             PreparedStatement ins = cn.prepareStatement(insertSql)) {
            while (rs.next()) {
                UUID msgId = (UUID) rs.getObject("id");
                String text = rs.getString("text");
                var hits = YouTubeUrlExtractor.extract(text);
                for (var hit : hits) {
                    Optional<YouTubeOEmbedClient.OEmbedData> data;
                    try {
                        data = client.fetch(hit.sourceUrl());
                    } catch (Exception e) {
                        log.debug("oEmbed fetch failed for {}: {}", hit.sourceUrl(), e.toString());
                        data = Optional.empty();
                    }
                    ins.setObject(1, UUID.randomUUID());
                    ins.setObject(2, msgId);
                    ins.setString(3, hit.kind());
                    ins.setString(4, hit.canonicalId());
                    ins.setString(5, hit.sourceUrl());
                    ins.setString(6, data.map(YouTubeOEmbedClient.OEmbedData::title).orElse(null));
                    ins.setString(7, data.map(YouTubeOEmbedClient.OEmbedData::thumbnailUrl).orElse(null));
                    ins.executeUpdate();
                    count++;
                    try {
                        Thread.sleep(THROTTLE_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return count;
                    }
                }
            }
        }
        return count;
    }

    private static YouTubeOEmbedClient buildClient() {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(1500))
                .withReadTimeout(Duration.ofMillis(1500));
        RestClient rc = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
        return new YouTubeOEmbedClient(rc, DEFAULT_OEMBED);
    }
}
```

- [ ] **Step 6.4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.hackathon.features.messages.embeds.BackfillMigrationTest'`
Expected: `BUILD SUCCESSFUL` — one test green.

- [ ] **Step 6.5: Run the whole backend suite to confirm no regression**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, 277 + new tests all passing.

- [ ] **Step 6.6: Commit**

```bash
git add backend/src/main/resources/db/migration/V13__backfill_embeds.java \
        backend/src/test/java/com/hackathon/features/messages/embeds/BackfillMigrationTest.java
git commit -m "$(cat <<'EOF'
feat(embeds): V13 Flyway migration backfills historical messages

Walks messages + direct_messages, extracts YT URLs, calls oEmbed with a
200 ms throttle, INSERT ... ON CONFLICT DO NOTHING. Never fails on per-row
oEmbed errors — row persists with null title/thumbnail_url and migration
continues.
EOF
)"
```

---

## Task 7: Frontend — DTO types + YouTubeEmbed component update

**Goal:** Extend the frontend `Message` / `DirectMessage` types with an `embeds` field, reshape `YouTubeEmbed` to prefer DTO-provided embeds (rendering title + thumbnail) and fall back to the existing regex for unbackfilled rows.

**Files:**
- Modify: `frontend/src/types/room.ts`
- Modify: `frontend/src/types/directMessage.ts`
- Modify: `frontend/src/components/YouTubeEmbed.tsx`
- Modify: `frontend/src/components/MessageItem.tsx`
- Create: `frontend/src/components/__tests__/YouTubeEmbed.test.tsx`

- [ ] **Step 7.1: Add the `Embed` type + `embeds` field to the room message type**

In `frontend/src/types/room.ts`, add near the top (after existing imports, before `Message`):

```ts
export interface Embed {
  id: string;
  kind: 'youtube' | string;
  canonicalId: string;
  sourceUrl: string;
  title: string | null;
  thumbnailUrl: string | null;
}
```

Add `embeds: Embed[];` to the `Message` interface as the last field (optional chaining on render handles older payloads).

- [ ] **Step 7.2: Add `embeds` to `directMessage.ts`**

In `frontend/src/types/directMessage.ts` add `import type { Embed } from './room';` and `embeds: Embed[];` to the `DirectMessage` interface.

- [ ] **Step 7.3: Write the failing Vitest case**

Create `frontend/src/components/__tests__/YouTubeEmbed.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { YouTubeEmbed } from '../YouTubeEmbed';

describe('YouTubeEmbed', () => {
  it('renders from DTO-provided embed when present', () => {
    render(
      <YouTubeEmbed
        text="ignored"
        embeds={[{
          id: 'e1',
          kind: 'youtube',
          canonicalId: 'dQw4w9WgXcQ',
          sourceUrl: 'https://youtu.be/dQw4w9WgXcQ',
          title: 'Rick Astley',
          thumbnailUrl: 'https://img/rick.jpg',
        }]}
      />,
    );
    expect(screen.getByTitle(/YouTube video dQw4w9WgXcQ/)).toBeInTheDocument();
    expect(screen.getByText('Rick Astley')).toBeInTheDocument();
  });

  it('falls back to regex when embeds is empty', () => {
    render(
      <YouTubeEmbed
        text="watch https://youtu.be/dQw4w9WgXcQ now"
        embeds={[]}
      />,
    );
    expect(screen.getByTitle(/YouTube video dQw4w9WgXcQ/)).toBeInTheDocument();
  });

  it('renders nothing when no embed and no regex hit', () => {
    const { container } = render(
      <YouTubeEmbed text="plain text" embeds={[]} />,
    );
    expect(container.querySelector('iframe')).toBeNull();
  });
});
```

- [ ] **Step 7.4: Run the test to verify failure**

Run: `cd frontend && npm test -- --run YouTubeEmbed`
Expected: FAIL — `YouTubeEmbed` current signature doesn't accept `text` / `embeds` props.

- [ ] **Step 7.5: Reshape `YouTubeEmbed`**

Replace `frontend/src/components/YouTubeEmbed.tsx` with:

```tsx
import React from 'react';
import type { Embed } from '../types/room';
import { extractYouTubeIds } from '../utils/youtube';

interface Props {
  text: string | null | undefined;
  embeds?: Embed[];
}

export const YouTubeEmbed: React.FC<Props> = ({ text, embeds }) => {
  // Prefer DTO-provided embeds; fall back to regex for unbackfilled messages.
  const youtubeEmbeds = (embeds ?? []).filter((e) => e.kind === 'youtube');
  const items: { id: string; title: string | null; thumbnailUrl: string | null }[] =
    youtubeEmbeds.length > 0
      ? youtubeEmbeds.map((e) => ({
          id: e.canonicalId,
          title: e.title,
          thumbnailUrl: e.thumbnailUrl,
        }))
      : extractYouTubeIds(text).map((id) => ({ id, title: null, thumbnailUrl: null }));

  if (items.length === 0) return null;

  return (
    <>
      {items.map((it) => (
        <div key={it.id} className="mt-2 max-w-xl">
          {it.title && (
            <div className="text-sm font-medium mb-1 dark:text-discord-text">{it.title}</div>
          )}
          <div className="relative w-full" style={{ aspectRatio: '16 / 9' }}>
            <iframe
              src={`https://www.youtube-nocookie.com/embed/${it.id}`}
              title={`YouTube video ${it.id}`}
              className="absolute inset-0 w-full h-full rounded border dark:border-discord-border"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
              loading="lazy"
            />
          </div>
        </div>
      ))}
    </>
  );
};
```

- [ ] **Step 7.6: Update `MessageItem.tsx` to pass `embeds`**

In `frontend/src/components/MessageItem.tsx`, find the existing `<YouTubeEmbed ... />` usage. Grep first:

Run: `grep -n YouTubeEmbed frontend/src/components/MessageItem.tsx`

Replace each call site (there may be one or two — both rendered message body and reply preview) from:

```tsx
<YouTubeEmbed videoId={id} />
```

or an iterator over `extractYouTubeIds(message.text)`, with a single:

```tsx
<YouTubeEmbed text={message.text} embeds={message.embeds} />
```

(Delete the old `extractYouTubeIds` / `.map((id) => <YouTubeEmbed ...>)` pattern — `YouTubeEmbed` now iterates internally.)

- [ ] **Step 7.7: Build and run all frontend tests**

Run: `cd frontend && npm run build`
Expected: `✓ built in ...` — TypeScript accepts the new fields.

Run: `cd frontend && npm test -- --run`
Expected: all tests pass (69 previous + 3 new).

- [ ] **Step 7.8: Commit**

```bash
git add frontend/src/types/room.ts \
        frontend/src/types/directMessage.ts \
        frontend/src/components/YouTubeEmbed.tsx \
        frontend/src/components/MessageItem.tsx \
        frontend/src/components/__tests__/YouTubeEmbed.test.tsx
git commit -m "$(cat <<'EOF'
feat(embeds): frontend consumes DTO-provided embeds with regex fallback

YouTubeEmbed now takes { text, embeds } and prefers DTO data (rendering
the oEmbed-cached title above the iframe) when present. Regex fallback
covers any message sent between V12 ship and V13 completion. MessageItem
stops iterating extractYouTubeIds itself.
EOF
)"
```

---

## Task 8: Playwright verification + roadmap update

**Goal:** Extend an existing e2e spec to prove the iframe renders when a YT link is sent, and mark Feature #11 complete in the roadmap.

**Files:**
- Modify: `frontend/e2e/chat-layout.spec.ts`
- Modify: `FEATURES_ROADMAP.md`

- [ ] **Step 8.1: Restart the stack with the new code**

Run: `docker compose up -d --build backend frontend`
Expected: backend applies V12 + V13 on startup (V13 logs `V13 backfill complete: 0 room embeds, 0 DM embeds` on a fresh DB).

- [ ] **Step 8.2: Extend `chat-layout.spec.ts` with a YouTube-embed assertion**

In `frontend/e2e/chat-layout.spec.ts`, add a new test block at the bottom (inside the existing `describe`):

```ts
  test('sending a YouTube link renders an inline iframe', async ({ page }) => {
    // Assumes the describe-level beforeEach creates a room and signs the user in.
    const composer = page.getByPlaceholder(/Type a message/i);
    await composer.fill('check this https://youtu.be/dQw4w9WgXcQ');
    await composer.press('Control+Enter');

    // Iframe with title "YouTube video <id>" must appear.
    await expect(page.locator('iframe[title="YouTube video dQw4w9WgXcQ"]'))
        .toBeVisible({ timeout: 5000 });
  });
```

If the existing describe doesn't have a beforeEach that creates a room and logs in, copy the setup from the first test in the same file — do NOT duplicate lazily with stale values.

- [ ] **Step 8.3: Run the e2e suite**

Run: `cd frontend && npx playwright test chat-layout --reporter=list`
Expected: all `chat-layout.spec.ts` scenarios pass including the new one.

- [ ] **Step 8.4: Run the whole e2e suite**

Run: `cd frontend && npx playwright test --reporter=list`
Expected: all 22 previous + 1 new = 23 scenarios pass.

- [ ] **Step 8.5: Mark the roadmap complete**

In `FEATURES_ROADMAP.md`, change the Feature #11 entry:

Replace:

```md
### Feature #11: Server-side embed metadata (split out of #10)
- Parse embed URLs (YouTube, future: Twitter/X, Spotify, generic OG) on send
- Persist `message_embeds` table per message with `kind`, `source_url`, `canonical_id`, cached `title`, `thumbnail_url`
- Expose on DTOs so clients get pre-parsed metadata instead of each re-running regex
- Enables: server-side moderation (ban a video across rooms), richer previews (thumbnails + titles), search-by-embed-type
- Split out of Feature #10 because the frontend-only approach gets us the primary UX win (inline video player) with zero schema change; server-side metadata is an enhancement, not a blocker
- **Status: TODO**
```

With:

```md
### Feature #11: Server-side embed metadata (split out of #10) ✅
- YouTube URL extraction in Java mirrors `frontend/src/utils/youtube.ts` (watch, youtu.be, shorts, embed) — `YouTubeUrlExtractor` pure function
- `YouTubeOEmbedClient` calls `https://www.youtube.com/oembed` synchronously with a 1.5 s timeout; any failure returns `Optional.empty()` and the row persists with `title = null, thumbnail_url = null`
- V12 migration adds `message_embeds` + `direct_message_embeds` (UNIQUE(message_id, canonical_id), CASCADE on message delete, index on `canonical_id` for future cross-room moderation)
- V13 Flyway Java migration backfills pre-existing messages with a 200 ms throttle; never fails per-row
- `EmbedService` wired into `MessageService` / `DirectMessageService` send + edit (reconcile removes rows whose URL was removed in an edit)
- DTO expansion: `ChatMessageDTO` / `DirectMessageDTO` gain `embeds: List<EmbedDto>` populated via `MessageEmbedRepository.findByMessageId`
- Frontend: `YouTubeEmbed` reshaped to `{ text, embeds }`; prefers DTO data (renders cached `title` above the iframe), falls back to `extractYouTubeIds` regex for unbackfilled rows as a safety net
- Backend tests: `YouTubeUrlExtractorTest` (9 URL-shape cases), `YouTubeOEmbedClientTest` (MockWebServer: 200/404/429/timeout/malformed), `EmbedServiceTest` (5 scenarios including reconcile), `EmbedFlowIntegrationTest` (round-trip), `BackfillMigrationTest`
- Frontend tests: 3 new Vitest cases on `YouTubeEmbed`; Playwright e2e asserts the iframe renders on send
- Spec: `docs/superpowers/specs/2026-04-20-server-side-embed-metadata-design.md`
- Plan: `docs/superpowers/plans/2026-04-20-server-side-embed-metadata.md`
- Completed 2026-04-20
- **Status: COMPLETE**
```

Also update the `## Progress` footer:

Replace:
```md
- **Completed:** 12 execution slots ...
- **In progress:** 0
- **Remaining:** 1 (Server-side Embed Metadata)
```

With:
```md
- **Completed:** 13 execution slots (Features #1, #2, #3, #4, App Shell Refactor, Message Content, Account Management, Attachments, YouTube Embeds, Presence, Sessions Management, Password Reset, Server-side Embed Metadata) + polish (emoji picker + reactions, chat ordering, light/dark theme)
- **In progress:** 0
- **Remaining:** 0
```

- [ ] **Step 8.6: Final green check**

Run: `cd backend && ./gradlew test` — expect BUILD SUCCESSFUL.
Run: `cd frontend && npm run build && npm test -- --run` — expect all green.
Run: `cd frontend && npx playwright test --reporter=list` — expect all 23 scenarios pass.

- [ ] **Step 8.7: Commit**

```bash
git add frontend/e2e/chat-layout.spec.ts FEATURES_ROADMAP.md
git commit -m "$(cat <<'EOF'
test(embeds): Playwright iframe-renders-on-send + roadmap #11 complete

Marks Feature #11 as COMPLETE with the matching roadmap entry. Final
roadmap state: 13/13 slots done.
EOF
)"
```

---

## Done

After Task 8:

- **Backend:** ~277 + 23 = 300 tests green (extractor 9, client 5, service 5, integration 1, backfill 1, plus existing).
- **Frontend:** 72 unit tests green.
- **E2E:** 23 scenarios green.
- Roadmap: 13/13 complete.
- Feature #11 shipped end-to-end: extract-on-send, persist, expose on DTO, backfill, render cached titles. Future kinds (`twitter`, `spotify`, `og`) plug into the same schema without another migration.
