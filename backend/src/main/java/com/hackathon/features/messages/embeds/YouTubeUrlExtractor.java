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
