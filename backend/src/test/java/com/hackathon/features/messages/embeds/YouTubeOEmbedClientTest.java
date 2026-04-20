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
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

class YouTubeOEmbedClientTest {

    private MockWebServer server;
    private YouTubeOEmbedClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(500))
                .withReadTimeout(Duration.ofMillis(500));
        RestClient rc = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
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
