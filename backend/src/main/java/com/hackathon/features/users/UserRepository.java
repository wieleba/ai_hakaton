package com.hackathon.features.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  java.util.List<User> findByXmppPasswordIsNotNull();

  @org.springframework.data.jpa.repository.Query(
      "SELECT u FROM User u "
          + "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "AND u.id <> :callerId "
          + "ORDER BY u.username")
  java.util.List<User> searchUsersExcludingCaller(String q, UUID callerId, org.springframework.data.domain.Pageable pageable);
}
