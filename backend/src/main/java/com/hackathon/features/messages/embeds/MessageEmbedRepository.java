package com.hackathon.features.messages.embeds;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageEmbedRepository extends JpaRepository<MessageEmbed, UUID> {

    List<MessageEmbed> findByMessageId(UUID messageId);

    List<MessageEmbed> findByMessageIdIn(Collection<UUID> messageIds);

    @Modifying
    @Query("delete from MessageEmbed e where e.messageId = :messageId and e.canonicalId not in :keep")
    void deleteByMessageIdAndCanonicalIdNotIn(
            @Param("messageId") UUID messageId, @Param("keep") Collection<String> keep);

    @Modifying
    @Query("delete from MessageEmbed e where e.messageId = :messageId")
    void deleteByMessageId(@Param("messageId") UUID messageId);
}
