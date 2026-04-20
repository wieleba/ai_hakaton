package com.hackathon.features.messages.embeds;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class EmbedWriter {

    private final MessageEmbedRepository messageEmbedRepo;
    private final DirectMessageEmbedRepository dmEmbedRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistMessageEmbeds(UUID messageId, List<MessageEmbed> rows) {
        for (MessageEmbed row : rows) {
            messageEmbedRepo.save(row);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void reconcileMessageEmbeds(UUID messageId, List<MessageEmbed> rows, List<String> keepCanonicalIds) {
        if (rows.isEmpty()) {
            messageEmbedRepo.deleteByMessageId(messageId);
            return;
        }
        messageEmbedRepo.deleteByMessageIdAndCanonicalIdNotIn(messageId, keepCanonicalIds);
        for (MessageEmbed row : rows) {
            messageEmbedRepo.save(row);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistDirectMessageEmbeds(UUID dmId, List<DirectMessageEmbed> rows) {
        for (DirectMessageEmbed row : rows) {
            dmEmbedRepo.save(row);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void reconcileDirectMessageEmbeds(UUID dmId, List<DirectMessageEmbed> rows, List<String> keepCanonicalIds) {
        if (rows.isEmpty()) {
            dmEmbedRepo.deleteByDirectMessageId(dmId);
            return;
        }
        dmEmbedRepo.deleteByDirectMessageIdAndCanonicalIdNotIn(dmId, keepCanonicalIds);
        for (DirectMessageEmbed row : rows) {
            dmEmbedRepo.save(row);
        }
    }
}
