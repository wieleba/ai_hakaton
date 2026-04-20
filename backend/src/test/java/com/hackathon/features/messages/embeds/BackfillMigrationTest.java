package com.hackathon.features.messages.embeds;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectConversationRepository;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageRepository;
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
import org.junit.jupiter.api.AfterAll;
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

    @AfterAll
    static void tearDown() throws IOException {
        System.clearProperty("app.oembed.youtube-url");
        if (oEmbedServer != null) {
            oEmbedServer.shutdown();
        }
    }

    @Autowired Flyway flyway;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired MessageRepository messages;
    @Autowired MessageEmbedRepository embeds;
    @Autowired DirectConversationRepository conversations;
    @Autowired DirectMessageRepository dms;
    @Autowired DirectMessageEmbedRepository dmEmbeds;

    /** Helper that runs V13 against the current (already-seeded) DB. */
    private void runMigration() {
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
    }

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
        runMigration();

        List<MessageEmbed> rows = embeds.findByMessageId(m.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCanonicalId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(rows.get(0).getTitle()).isEqualTo("T");
    }

    @Test
    void backfillCoversDirectMessagesToo() {
        // Seed two users and a direct conversation between them.
        User sender = users.save(User.builder()
                .email("dm-sender@test")
                .username("dm-sender")
                .passwordHash("x")
                .build());
        User receiver = users.save(User.builder()
                .email("dm-receiver@test")
                .username("dm-receiver")
                .passwordHash("x")
                .build());
        // canonical_user_order constraint requires user1_id < user2_id (PostgreSQL UUID string ordering).
        boolean senderFirst = sender.getId().toString().compareTo(receiver.getId().toString()) < 0;
        DirectConversation conv = conversations.save(DirectConversation.builder()
                .user1Id(senderFirst ? sender.getId() : receiver.getId())
                .user2Id(senderFirst ? receiver.getId() : sender.getId())
                .build());
        DirectMessage dm = dms.save(DirectMessage.builder()
                .conversationId(conv.getId())
                .senderId(sender.getId())
                .text("check this out https://youtu.be/oHg5SJYRHA0").build());

        // Clear any existing DM embeds and re-run the migration.
        dmEmbeds.deleteAll();
        int requestsBefore = oEmbedServer.getRequestCount();
        runMigration();

        List<DirectMessageEmbed> rows = dmEmbeds.findByDirectMessageId(dm.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCanonicalId()).isEqualTo("oHg5SJYRHA0");
        assertThat(rows.get(0).getTitle()).isEqualTo("T");

        // Verify the mock server was actually called (at least 1 request for the DM).
        assertThat(oEmbedServer.getRequestCount()).isGreaterThanOrEqualTo(requestsBefore + 1);
    }

    @Test
    void backfillCallsMockForBothRoomAndDm() {
        // Seed one room message and one DM message so both branches are exercised
        // in a single migration run, then assert the mock was hit at least twice.
        User u = users.save(User.builder()
                .email("combined@test")
                .username("combined")
                .passwordHash("x")
                .build());
        User u2 = users.save(User.builder()
                .email("combined2@test")
                .username("combined2")
                .passwordHash("x")
                .build());
        ChatRoom r = rooms.save(ChatRoom.builder()
                .name("combined-room").ownerId(u.getId()).visibility("public").build());
        Message m = messages.save(Message.builder()
                .roomId(r.getId()).userId(u.getId())
                .text("https://youtu.be/9bZkp7q19f0").build());
        // canonical_user_order constraint requires user1_id < user2_id (PostgreSQL UUID string ordering).
        boolean uFirst = u.getId().toString().compareTo(u2.getId().toString()) < 0;
        DirectConversation conv = conversations.save(DirectConversation.builder()
                .user1Id(uFirst ? u.getId() : u2.getId())
                .user2Id(uFirst ? u2.getId() : u.getId())
                .build());
        DirectMessage dm = dms.save(DirectMessage.builder()
                .conversationId(conv.getId())
                .senderId(u.getId())
                .text("https://youtu.be/kffacxfA7G4").build());

        embeds.deleteAll();
        dmEmbeds.deleteAll();
        int requestsBefore = oEmbedServer.getRequestCount();
        runMigration();

        assertThat(embeds.findByMessageId(m.getId())).hasSize(1);
        assertThat(dmEmbeds.findByDirectMessageId(dm.getId())).hasSize(1);
        // At least one request for the room message + one for the DM message.
        assertThat(oEmbedServer.getRequestCount()).isGreaterThanOrEqualTo(requestsBefore + 2);
    }
}
