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
