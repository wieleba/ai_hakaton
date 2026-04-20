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
        String url = oEmbedServer.url("/oembed").toString();
        // Set as system property so V13__backfill_embeds.buildClient() picks it up
        // (migration runs outside Spring context, cannot read Spring properties directly).
        System.setProperty("app.oembed.youtube-url", url);
        r.add("app.oembed.youtube-url", () -> url);
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
