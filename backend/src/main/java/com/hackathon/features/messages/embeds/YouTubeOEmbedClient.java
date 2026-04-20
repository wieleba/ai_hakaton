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
        String url = UriComponentsBuilder.fromUriString(endpoint)
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
            // Swallow all failures — the send path must never fail because of oEmbed.
            // Surfaced at WARN so prolonged outages (YouTube down / rate-limiting) remain
            // visible in production logs without breaking message send.
            log.warn("oEmbed fetch failed for {}: {}", sourceUrl, e.toString());
            return Optional.empty();
        }
    }
}
