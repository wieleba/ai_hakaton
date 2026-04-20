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
