package com.hackathon.features.messages.embeds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.JwtTokenProvider;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired ChatRoomService roomService;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;

    @Test
    void sendMessageWithYouTubeUrl_roundTripsEmbed() throws Exception {
        oEmbedServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"title\":\"Never Gonna Give You Up\","
                        + "\"thumbnail_url\":\"https://img/ricky.jpg\"}"));

        long t = System.nanoTime();
        User author = userService.registerUser(
                "embed" + t + "@test.com", "embedder" + t, "password123");
        ChatRoom room = roomService.createRoom("e-room-" + t, null, author.getId(), "public");
        String token = jwtTokenProvider.generateToken(author.getId(), author.getUsername());

        String body = "{\"text\":\"check https://youtu.be/dQw4w9WgXcQ\"}";
        mvc.perform(post("/api/rooms/" + room.getId() + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // The AFTER_COMMIT event listener runs asynchronously relative to the HTTP response;
        // poll the GET endpoint until the embed row appears (up to 3 seconds).
        JsonNode embeds = null;
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mvc.perform(get("/api/rooms/" + room.getId() + "/messages")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode msgs = objectMapper.readTree(res.getResponse().getContentAsString());
            assertThat(msgs.isArray()).isTrue();
            assertThat(msgs.size()).isEqualTo(1);
            embeds = msgs.get(0).get("embeds");
            if (embeds != null && embeds.size() > 0) break;
            Thread.sleep(100);
        }

        assertThat(embeds).isNotNull();
        assertThat(embeds.size()).isEqualTo(1);
        assertThat(embeds.get(0).get("kind").asText()).isEqualTo("youtube");
        assertThat(embeds.get(0).get("canonicalId").asText()).isEqualTo("dQw4w9WgXcQ");
        assertThat(embeds.get(0).get("title").asText()).isEqualTo("Never Gonna Give You Up");
        assertThat(embeds.get(0).get("thumbnailUrl").asText()).isEqualTo("https://img/ricky.jpg");
    }
}
