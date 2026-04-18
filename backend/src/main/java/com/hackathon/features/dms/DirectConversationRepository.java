package com.hackathon.features.dms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectConversationRepository extends JpaRepository<DirectConversation, UUID> {
  Optional<DirectConversation> findByUser1IdAndUser2Id(UUID user1Id, UUID user2Id);

  @Query(
      "SELECT c FROM DirectConversation c WHERE c.user1Id = :userId OR c.user2Id = :userId "
          + "ORDER BY c.createdAt DESC")
  List<DirectConversation> findAllForUser(UUID userId);
}
