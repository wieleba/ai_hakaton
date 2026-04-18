package com.hackathon.features.friendships;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

  Optional<Friendship> findByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);

  List<Friendship> findByAddresseeIdAndStatus(UUID addresseeId, String status);

  List<Friendship> findByRequesterIdAndStatus(UUID requesterId, String status);

  @Query(
      "SELECT f FROM Friendship f WHERE "
          + "(f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'accepted'")
  List<Friendship> findAcceptedForUser(UUID userId);

  @Query(
      "SELECT f FROM Friendship f WHERE "
          + "((f.requesterId = :a AND f.addresseeId = :b) OR "
          + " (f.requesterId = :b AND f.addresseeId = :a))")
  Optional<Friendship> findBetween(UUID a, UUID b);
}
