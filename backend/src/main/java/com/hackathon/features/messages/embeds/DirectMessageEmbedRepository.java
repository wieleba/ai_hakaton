package com.hackathon.features.messages.embeds;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageEmbedRepository extends JpaRepository<DirectMessageEmbed, UUID> {

    List<DirectMessageEmbed> findByDirectMessageId(UUID directMessageId);

    List<DirectMessageEmbed> findByDirectMessageIdIn(Collection<UUID> directMessageIds);

    @Modifying
    @Query("delete from DirectMessageEmbed e "
         + "where e.directMessageId = :dmId and e.canonicalId not in :keep")
    void deleteByDirectMessageIdAndCanonicalIdNotIn(
            @Param("dmId") UUID dmId, @Param("keep") Collection<String> keep);

    @Modifying
    @Query("delete from DirectMessageEmbed e where e.directMessageId = :dmId")
    void deleteByDirectMessageId(@Param("dmId") UUID dmId);
}
