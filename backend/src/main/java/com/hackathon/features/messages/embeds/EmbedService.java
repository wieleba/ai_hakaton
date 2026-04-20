package com.hackathon.features.messages.embeds;

import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.messages.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Builds YouTube embed metadata for messages off the send/edit transaction.
 *
 * <p>Called via Spring events fired AFTER_COMMIT by MessageService /
 * DirectMessageService, so the blocking oEmbed HTTP call never runs inside a
 * DB transaction that holds the messages-row lock. Public methods are still
 * exposed for direct invocation (tests + backfill migration).
 */
@Service
@RequiredArgsConstructor
public class EmbedService {

    private final YouTubeOEmbedClient oEmbedClient;
    private final EmbedWriter writer;

    // --- Event listeners (run after the outer send/edit tx commits) ------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageEmbedEvents.MessageCreated event) {
        persistForMessage(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageEdited(MessageEmbedEvents.MessageEdited event) {
        reconcileForMessage(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDirectMessageCreated(MessageEmbedEvents.DirectMessageCreated event) {
        persistForDirectMessage(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDirectMessageEdited(MessageEmbedEvents.DirectMessageEdited event) {
        reconcileForDirectMessage(event.message());
    }

    // --- Public API (direct invocation from tests + V13 backfill migration) ----

    public void persistForMessage(Message m) {
        List<MessageEmbed> rows = buildMessageEmbeds(m);
        if (rows.isEmpty()) return;
        writer.persistMessageEmbeds(m.getId(), rows);
    }

    public void reconcileForMessage(Message m) {
        List<YouTubeUrlExtractor.Extracted> hits = YouTubeUrlExtractor.extract(m.getText());
        List<MessageEmbed> rows = buildMessageEmbedsFrom(m.getId(), hits);
        List<String> keep = hits.stream().map(YouTubeUrlExtractor.Extracted::canonicalId).toList();
        writer.reconcileMessageEmbeds(m.getId(), rows, keep);
    }

    public void persistForDirectMessage(DirectMessage m) {
        List<DirectMessageEmbed> rows = buildDirectMessageEmbeds(m);
        if (rows.isEmpty()) return;
        writer.persistDirectMessageEmbeds(m.getId(), rows);
    }

    public void reconcileForDirectMessage(DirectMessage m) {
        List<YouTubeUrlExtractor.Extracted> hits = YouTubeUrlExtractor.extract(m.getText());
        List<DirectMessageEmbed> rows = buildDirectMessageEmbedsFrom(m.getId(), hits);
        List<String> keep = hits.stream().map(YouTubeUrlExtractor.Extracted::canonicalId).toList();
        writer.reconcileDirectMessageEmbeds(m.getId(), rows, keep);
    }

    // --- Helpers (fetch oEmbed outside any transaction) -----------------------

    private List<MessageEmbed> buildMessageEmbeds(Message m) {
        return buildMessageEmbedsFrom(m.getId(), YouTubeUrlExtractor.extract(m.getText()));
    }

    private List<MessageEmbed> buildMessageEmbedsFrom(UUID messageId, List<YouTubeUrlExtractor.Extracted> hits) {
        List<MessageEmbed> out = new ArrayList<>();
        for (YouTubeUrlExtractor.Extracted hit : hits) {
            Optional<YouTubeOEmbedClient.OEmbedData> data = oEmbedClient.fetch(hit.sourceUrl());
            out.add(MessageEmbed.builder()
                    .messageId(messageId)
                    .kind(hit.kind())
                    .canonicalId(hit.canonicalId())
                    .sourceUrl(hit.sourceUrl())
                    .title(data.map(YouTubeOEmbedClient.OEmbedData::title).orElse(null))
                    .thumbnailUrl(data.map(YouTubeOEmbedClient.OEmbedData::thumbnailUrl).orElse(null))
                    .build());
        }
        return out;
    }

    private List<DirectMessageEmbed> buildDirectMessageEmbeds(DirectMessage m) {
        return buildDirectMessageEmbedsFrom(m.getId(), YouTubeUrlExtractor.extract(m.getText()));
    }

    private List<DirectMessageEmbed> buildDirectMessageEmbedsFrom(UUID dmId, List<YouTubeUrlExtractor.Extracted> hits) {
        List<DirectMessageEmbed> out = new ArrayList<>();
        for (YouTubeUrlExtractor.Extracted hit : hits) {
            Optional<YouTubeOEmbedClient.OEmbedData> data = oEmbedClient.fetch(hit.sourceUrl());
            out.add(DirectMessageEmbed.builder()
                    .directMessageId(dmId)
                    .kind(hit.kind())
                    .canonicalId(hit.canonicalId())
                    .sourceUrl(hit.sourceUrl())
                    .title(data.map(YouTubeOEmbedClient.OEmbedData::title).orElse(null))
                    .thumbnailUrl(data.map(YouTubeOEmbedClient.OEmbedData::thumbnailUrl).orElse(null))
                    .build());
        }
        return out;
    }
}
