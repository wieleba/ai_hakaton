package db.migration;

import com.hackathon.features.messages.embeds.YouTubeOEmbedClient;
import com.hackathon.features.messages.embeds.YouTubeUrlExtractor;
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
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

/**
 * One-off backfill of message_embeds + direct_message_embeds for rows that
 * pre-date V12. Safe to re-run: ON CONFLICT DO NOTHING on (message_id,
 * canonical_id). Never fails per-row oEmbed errors — rows get null
 * title/thumbnail and we move on.
 */
public class V13__backfill_embeds extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V13__backfill_embeds.class);
    // Spring property (system property) takes priority — tests inject the mock server URL this way.
    // Env var APP_OEMBED_YOUTUBE_URL is the production override path.
    private static String resolveOEmbedUrl() {
        String sysProp = System.getProperty("app.oembed.youtube-url");
        if (sysProp != null && !sysProp.isBlank()) return sysProp;
        return System.getenv().getOrDefault("APP_OEMBED_YOUTUBE_URL", "https://www.youtube.com/oembed");
    }
    private static final long THROTTLE_MS = 200L;

    @Override
    public void migrate(Context ctx) throws Exception {
        Connection cn = ctx.getConnection();
        YouTubeOEmbedClient client = buildClient(resolveOEmbedUrl());

        int roomTotal = backfillTable(
                cn, client,
                "SELECT id, text FROM messages WHERE text IS NOT NULL",
                "INSERT INTO message_embeds "
                        + "(id, message_id, kind, canonical_id, source_url, title, thumbnail_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (message_id, canonical_id) DO NOTHING");
        int dmTotal = backfillTable(
                cn, client,
                "SELECT id, text FROM direct_messages WHERE text IS NOT NULL",
                "INSERT INTO direct_message_embeds "
                        + "(id, direct_message_id, kind, canonical_id, source_url, title, thumbnail_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (direct_message_id, canonical_id) DO NOTHING");
        log.info("V13 backfill complete: {} room embeds, {} DM embeds", roomTotal, dmTotal);
    }

    private int backfillTable(
            Connection cn, YouTubeOEmbedClient client, String selectSql, String insertSql) throws Exception {
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

    private static YouTubeOEmbedClient buildClient(String endpoint) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(1500))
                .withReadTimeout(Duration.ofMillis(1500));
        RestClient rc = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        return new YouTubeOEmbedClient(rc, endpoint);
    }
}
