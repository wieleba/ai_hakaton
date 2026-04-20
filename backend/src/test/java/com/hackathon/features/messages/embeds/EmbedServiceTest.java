package com.hackathon.features.messages.embeds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hackathon.features.messages.Message;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbedServiceTest {

    @Mock MessageEmbedRepository messageEmbedRepo;
    @Mock DirectMessageEmbedRepository dmEmbedRepo;
    @Mock YouTubeOEmbedClient oEmbedClient;
    @InjectMocks EmbedService embedService;

    private Message msgWith(String text) {
        return Message.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .text(text)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void persistForMessage_noYouTubeLinks_doesNothing() {
        embedService.persistForMessage(msgWith("hello world"));
        verifyNoInteractions(oEmbedClient, messageEmbedRepo);
    }

    @Test
    void persistForMessage_storesOneRowPerDistinctVideo_withOEmbedMetadata() {
        Message m = msgWith("https://youtu.be/AAAAAAAAAAA and https://youtu.be/BBBBBBBBBBB");
        when(oEmbedClient.fetch(any()))
                .thenReturn(Optional.of(new YouTubeOEmbedClient.OEmbedData("T1", "http://img/1")))
                .thenReturn(Optional.of(new YouTubeOEmbedClient.OEmbedData("T2", "http://img/2")));

        embedService.persistForMessage(m);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(messageEmbedRepo, times(2)).save(captor.capture());
        List<MessageEmbed> saved = captor.getAllValues();
        org.assertj.core.api.Assertions.assertThat(saved)
                .extracting(MessageEmbed::getCanonicalId, MessageEmbed::getTitle, MessageEmbed::getThumbnailUrl)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AAAAAAAAAAA", "T1", "http://img/1"),
                        org.assertj.core.groups.Tuple.tuple("BBBBBBBBBBB", "T2", "http://img/2"));
    }

    @Test
    void persistForMessage_oEmbedFailureStillPersistsRow_withNulls() {
        Message m = msgWith("https://youtu.be/AAAAAAAAAAA");
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());

        embedService.persistForMessage(m);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(messageEmbedRepo).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTitle()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getThumbnailUrl()).isNull();
    }

    @Test
    void reconcileForMessage_removesVanishedVideos_andInsertsNew() {
        Message m = msgWith("now only https://youtu.be/CCCCCCCCCCC");
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());

        embedService.reconcileForMessage(m);

        verify(messageEmbedRepo).deleteByMessageIdAndCanonicalIdNotIn(
                m.getId(), List.of("CCCCCCCCCCC"));
        verify(messageEmbedRepo).save(any(MessageEmbed.class));
    }

    @Test
    void reconcileForMessage_noLinksLeft_deletesEverything() {
        Message m = msgWith("plain edit no urls");
        embedService.reconcileForMessage(m);
        verify(messageEmbedRepo).deleteByMessageId(m.getId());
        verifyNoInteractions(oEmbedClient);
    }
}
