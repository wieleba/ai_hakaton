package com.hackathon.features.messages.embeds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "direct_message_embeds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageEmbed {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "direct_message_id", nullable = false)
    private UUID directMessageId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "canonical_id", nullable = false, length = 64)
    private String canonicalId;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
